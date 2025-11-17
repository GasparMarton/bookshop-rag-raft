package my.bookshop.handlers;

import cds.gen.adminservice.AdminService_;
import cds.gen.adminservice.GenreHierarchy;
import cds.gen.adminservice.GenreHierarchyMoveSiblingContext;
import cds.gen.adminservice.GenreHierarchy_;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.List;
import my.bookshop.repository.adminservice.GenreHierarchyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ServiceName(AdminService_.CDS_NAME)
/**
 * Example of a custom handler for nextSiblingAction
 */
public class HierarchySiblingActionHandler implements EventHandler {

    @Autowired
    private GenreHierarchyRepository genreHierarchyRepository;

    @On
    void onMoveSiblingAction(GenreHierarchy_ ref, GenreHierarchyMoveSiblingContext context) {
        // Find current node and its parent
        GenreHierarchy toMove = genreHierarchyRepository.loadNode(ref);

        // Find all children of the parent, which are siblings of the entry being moved
        List<GenreHierarchy> siblingNodes = genreHierarchyRepository.findSiblings(toMove.getParentId());

        int oldPosition = 0;
        int newPosition = siblingNodes.size();
        for (int i = 0; i < siblingNodes.size(); ++i) {
            GenreHierarchy sibling = siblingNodes.get(i);
            if (sibling.getId().equals(toMove.getId())) {
                oldPosition = i;
            }
            if (context.getNextSibling() != null && sibling.getId().equals(context.getNextSibling().getId())) {
                newPosition = i;
            }
        }

        // Move siblings
        siblingNodes.add(oldPosition < newPosition ? newPosition - 1 : newPosition, siblingNodes.remove(oldPosition));

        // Recalculate ranks
        for (int i = 0; i < siblingNodes.size(); ++i) {
            siblingNodes.get(i).setSiblingRank(i);
        }

        // Update DB
        genreHierarchyRepository.updateSiblings(siblingNodes);
        context.setCompleted();
    }
}
