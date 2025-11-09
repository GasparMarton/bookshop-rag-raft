package my.bookshop.config;

import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.reflect.CdsModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogConfiguration {

	@Bean
	public CqnAnalyzer catalogCqnAnalyzer(CdsModel model) {
		return CqnAnalyzer.create(model);
	}
}
