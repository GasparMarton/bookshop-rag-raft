package my.bookshop.handlers;

import static cds.gen.adminservice.AdminService_.ADDRESSES;

import cds.gen.adminservice.Addresses;
import cds.gen.adminservice.Addresses_;
import cds.gen.adminservice.AdminService_;
import cds.gen.adminservice.Orders;
import cds.gen.api_business_partner.ApiBusinessPartner;
import cds.gen.api_business_partner.ApiBusinessPartner_;
import cds.gen.api_business_partner.BusinessPartnerChangedContext;
import com.sap.cds.Result;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Predicate;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceConfiguration;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceConfiguration.TimeLimiterConfiguration;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceDecorator;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import my.bookshop.MessageKeys;
import my.bookshop.repository.adminservice.AddressesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Custom handler for the Admin Service Addresses, which come from a remote S/4 System
 */
@Component
@ServiceName(AdminService_.CDS_NAME)
public class AdminServiceAddressHandler implements EventHandler {

	private final static Logger logger = LoggerFactory.getLogger(AdminServiceAddressHandler.class);

	// We are mashing up the AdminService with two other services...
	@Autowired
	private AddressesRepository addressesRepository;

	@Autowired
	@Qualifier(ApiBusinessPartner_.CDS_NAME)
	private ApiBusinessPartner bupa;

	// Delegate ValueHelp requests to S/4 backend, fetching current user's addresses from there
	@On(entity = Addresses_.CDS_NAME)
	public void readAddresses(CdsReadEventContext context) {
		if(context.getCqn().ref().segments().size() != 1) {
			return; // no value help request
		}

		// add BusinessPartner where condition
		String businessPartner = context.getUserInfo().getAttributeValues("businessPartner").stream().findFirst()
			.orElseThrow(() -> new ServiceException(ErrorStatuses.FORBIDDEN, MessageKeys.BUPA_MISSING));

		CqnSelect select = CQL.copy(context.getCqn(), new Modifier() {

			public Predicate where(Predicate original) {
				Predicate where = CQL.get(Addresses.BUSINESS_PARTNER).eq(businessPartner);
				if(original != null) {
					where = original.and(where);
				}
				return where;
			}

		});

		// using Cloud SDK resilience capabilities..
		ResilienceConfiguration config = ResilienceConfiguration.of(AdminServiceAddressHandler.class)
			.timeLimiterConfiguration(TimeLimiterConfiguration.of(Duration.ofSeconds(10)));

		context.setResult(ResilienceDecorator.executeSupplier(() ->  {
			// ..to access the S/4 system in a resilient way..
			logger.info("Delegating GET Addresses to S/4 service");
			return bupa.run(select);
		}, config, (t) -> {
			// ..falling back to the already replicated addresses in our own database
			logger.warn("Falling back to already replicated Addresses");
			return addressesRepository.runSelect(select);
		}));
	}

	// Replicate chosen addresses from S/4 when filling orders
	@Before(event = { CqnService.EVENT_CREATE, CqnService.EVENT_UPDATE, DraftService.EVENT_DRAFT_PATCH })
	public void patchAddressId(EventContext context, Stream<Orders> orders) {
		String businessPartner = context.getUserInfo().getAttributeValues("businessPartner").stream().findFirst()
			.orElseThrow(() -> new ServiceException(ErrorStatuses.FORBIDDEN, MessageKeys.BUPA_MISSING));

		orders.filter(o -> o.getShippingAddressId() != null).forEach(order -> {
			String addressId = order.getShippingAddressId();
			Result replica = addressesRepository.findReplica(businessPartner, addressId);
			// check if the address was not yet replicated
			if(replica.rowCount() < 1) {
				logger.info("Replicating Address '{}' from S/4 service", addressId);
				Addresses remoteAddress = bupa.run(Select.from(ADDRESSES)
						.where(a -> a.businessPartner().eq(businessPartner).and(a.ID().eq(addressId))))
						.single();

				remoteAddress.setTombstone(false);
				addressesRepository.insertAddress(remoteAddress);
			}
			order.setShippingAddressBusinessPartner(businessPartner);
		});
	}

	@On(service = ApiBusinessPartner_.CDS_NAME)
	public void updateBusinessPartnerAddresses(BusinessPartnerChangedContext context) {
		logger.info(">> received: " + context.getData());
		String businessPartner = context.getData().getBusinessPartner();

		// fetch affected entries from local replicas
		Result replicas = addressesRepository.findReplicas(businessPartner);
		if(replicas.rowCount() > 0) {
			logger.info("Updating Addresses for BusinessPartner '{}'", businessPartner);
			// fetch changed data from S/4 -> might be less than local due to deletes
			List<Addresses> remoteAddresses = bupa.run(Select.from(ADDRESSES)
					.where(a -> a.businessPartner().eq(businessPartner)))
					.listOf(Addresses.class);
			List<Addresses> localReplicas = replicas.listOf(Addresses.class);
			// update replicas or add tombstone if external address was deleted
			localReplicas.forEach(rep -> {
				Optional<Addresses> matching = remoteAddresses
					.stream()
					.filter(ext -> ext.getId().equals(rep.getId()))
					.findFirst();

				if(matching.isEmpty()) {
					rep.setTombstone(true);
				} else {
					matching.get().forEach(rep::put);
				}
			});
			// update local replicas with changes from S/4
			addressesRepository.upsertAddresses(localReplicas);
		}
	}

}
