package uk.ac.cam.echo2016.multinarrative;

import java.util.ArrayList;
import java.util.HashMap;

import android.os.BaseBundle;

/**
 *
 * MODIFYING THIS CLASS WILL BREAK ALL TEMPLATE SAVE FILES. DO NOT DO SO UNLESS
 * ABSOLUTELY NECESSARY
 * 
 * The template of the story from which the copy of the game required for each
 * play through is derived.
 * 
 * <p>
 * ALT: A full {@code MultiNarrative} graph that is used to create new
 * {@code NarrativeInstance}s for each playthrough. This contains all
 * {@code Node}s and {@code Route}s on the graph designed in the {@code FXMLGUI}
 * editor, and is the template graph used for new save files. When the template
 * is created, it should not be modified in any way. (Unless the programmer
 * wants different behaviour across new playthroughs!)
 * 
 * @author tr393
 * @author rjm232
 * @author jr650
 * @version 1.0
 * @see NarrativeTemplate#generateInstance()
 * @see NarrativeInstance
 * @see MultiNarrative
 */
public class NarrativeTemplate extends MultiNarrative {
    private static final long serialVersionUID = 1;

    public NarrativeTemplate() {
    }

    public NarrativeTemplate(HashMap<String, Route> routes, HashMap<String, Node> nodes, SynchronizationNode start,
            BaseBundle properties) {
        this.routes = routes;
        this.nodes = nodes;
        this.start = start;
        this.properties = properties;
    }

    // TODO Documentation
    /**
     *
     * @return
     * @throws InvalidGraphException
     */
    public NarrativeInstance generateInstance() throws InvalidGraphException {
        HashMap<String, Node> r_nodes = new HashMap<>();
        HashMap<String, Route> r_routes = new HashMap<>();

        if (start == null) {
            throw new InvalidGraphException("Error: Graph does not have a start node.");
        }

        for (Node node : nodes.values()) {
            Node r_node = node.clone();
            r_node.createProperties();
            r_node.setExiting(new ArrayList<Route>());
            r_node.setEntering(new ArrayList<Route>());
            r_nodes.put(node.getId(), r_node);
        }

        for (Route route : routes.values()) {
            Route r_route = route.clone();
            // Find Start and end in r_nodes

            r_route.setStart(r_nodes.get(route.getStart().getId()));
            r_route.setEnd(r_nodes.get(route.getEnd().getId()));
            r_route.getStart().getExiting().add(r_route);
            r_route.getEnd().getEntering().add(r_route);

            r_routes.put(route.getId(), r_route);
        }
        SynchronizationNode r_start = (SynchronizationNode) r_nodes.get(start.getId());

        NarrativeInstance instance = new NarrativeInstance(r_routes, r_nodes, r_start,
                BaseBundle.deepcopy(this.properties));
        instance.setActive(r_start);
        instance.properties = BaseBundle.deepcopy(this.properties); // TODO add
                                                                    // to tests
        return instance;
    }

    public NarrativeInstance generateInstance2() throws InvalidGraphException {
        NarrativeInstance instance = new NarrativeInstance();

        if (start == null)
            throw new InvalidGraphException("Error: Graph does not have a start node.");
        instance.start = (SynchronizationNode) copyToInstance(this.start, instance);
        instance.setActive(instance.start);
        instance.properties = BaseBundle.deepcopy(this.properties); // TODO add
                                                                    // to tests
        return instance;
    }

    /**
     * Copies this node and its routes and recursively calls this method on the
     * nodes reached by the routes further down the graph. The copy created is
     * then returned. The graph instance is used to record node/route references
     * and make sure that nodes are not copied twice. The callConstructor method
     * is effectively a clone method. :P
     * 
     * @param instance
     */
    public Node copyToInstance(Node node, NarrativeInstance instance) { // TODO
                                                                        // More
                                                                        // Documentation

        // Eventually calls Node(this.id) via subclass's constructor
        Node result = node.create(node.getId());

        if (node.getProperties() != null) // Copy getProperties() across, if any
            result.setProperties(BaseBundle.deepcopy(node.getProperties()));

        // Copy each route leaving node node and call copyGraph on their end
        // nodes
        for (Route templateRoute : node.getExiting()) {
            Node endNodeCopy;
            if (!instance.nodes.containsKey(templateRoute.getEnd().getId())) {
                // Not already copied
                endNodeCopy = copyToInstance(templateRoute.getEnd(), instance); // Recursively
                                                                                // copy
                                                                                // nodes
                                                                                // at
                                                                                // the
                                                                                // ends
                                                                                // of
            } else {
                // Already copied
                endNodeCopy = instance.getNode(templateRoute.getEnd().getId()); // Get
                                                                                // reference
                                                                                // to
                                                                                // copied
                                                                                // end
            }

            // Create route using references obtained/created above, linking
            // node node to the new end nodes
            Route routeCopy = new Route(templateRoute.getId(), result, endNodeCopy);
            routeCopy.setup();
            routeCopy.setProperties(BaseBundle.deepcopy(templateRoute.getProperties()));

            // Update graph references
            instance.routes.put(routeCopy.getId(), routeCopy);
        }
        instance.nodes.put(result.getId(), result);
        return result;
    }
}
