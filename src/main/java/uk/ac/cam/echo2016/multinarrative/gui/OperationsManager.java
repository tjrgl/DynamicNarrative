package uk.ac.cam.echo2016.multinarrative.gui;

import java.io.IOException;
import java.util.ArrayList;

import android.os.BaseBundle;
import javafx.scene.control.CheckBox;
import javafx.scene.paint.Color;
import uk.ac.cam.echo2016.multinarrative.GUINarrative;
import uk.ac.cam.echo2016.multinarrative.NonUniqueStartException;
import uk.ac.cam.echo2016.multinarrative.Route;
import uk.ac.cam.echo2016.multinarrative.dev.Debug;
import uk.ac.cam.echo2016.multinarrative.gui.graph.GraphEdge;
import uk.ac.cam.echo2016.multinarrative.gui.graph.GraphNode;
import uk.ac.cam.echo2016.multinarrative.gui.operations.CompositeOperation;
import uk.ac.cam.echo2016.multinarrative.gui.operations.IllegalOperationException;
import uk.ac.cam.echo2016.multinarrative.gui.operations.Operation;
import uk.ac.cam.echo2016.multinarrative.gui.operations.OperationGenerator;
import uk.ac.cam.echo2016.multinarrative.gui.operations.UndoableOperationSequence;
import uk.ac.cam.echo2016.multinarrative.io.SaveReader;
import uk.ac.cam.echo2016.multinarrative.io.SaveWriter;

public class OperationsManager {

    private FXMLController controller;
    private ControllerOperations controllerOperations;
    private NarrativeOperations narrativeOperations;
    private UndoableOperationSequence sequence;
    private Generator generator = new Generator();
    
    public boolean dirtyFlag = false;

    public OperationsManager(FXMLController controller) {
        this.controller = controller;
        controllerOperations = new ControllerOperations(controller);
        narrativeOperations = new NarrativeOperations();
        sequence = new UndoableOperationSequence();
    }

    public void doOp(Operation o) throws IllegalOperationException {
        try {
            sequence.storeAndExecute(o);
            dirtyFlag = true;
        } catch (IllegalOperationException ioe) {
            Debug.logError(ioe, 5, Debug.SYSTEM_GUI);
            throw ioe;
        }
    }

    public void undo() throws IllegalOperationException {
        try {
            sequence.undoLastOperation();
        } catch (IllegalOperationException ioe) {
            Debug.logError(ioe, 5, Debug.SYSTEM_GUI);
            throw ioe;
        }
    }

    public void redo() throws IllegalOperationException {
        try {
            sequence.redoLastUndo();
        } catch (IllegalOperationException ioe) {
            Debug.logError(ioe, 5, Debug.SYSTEM_GUI);
            throw ioe;
        }
    }

    public void loadNarrative(String filename) throws IOException, IllegalOperationException {
        GUINarrative loaded = SaveReader.loadGUINarrative(filename);
        if (loaded == null) {
            throw new IOException();
        }
        controllerOperations.clearGraph();

        narrativeOperations.loadNarrative(loaded);
        controllerOperations.buildGraph(loaded);

        sequence.clearHistory();
        dirtyFlag = false;
    }

    public void saveNarrative(String filename) throws IOException {
        GUINarrative narrative = narrativeOperations.getNarrative();
        SaveWriter.saveObject(filename, narrative);
        dirtyFlag = false;
    }

    public void exportNarrative(String filename) throws IOException, IllegalOperationException {
        try {
            narrativeOperations.getNarrative().saveTemplate(filename);
        } catch (NonUniqueStartException e) {
            throw new IllegalOperationException(Strings.NON_UNIQUE_START);
        }
    }

    public NarrativeOperations narrativeOperations() {
        return narrativeOperations;
    }

    public ControllerOperations controllerOperations() {
        return controllerOperations;
    }

    public void newFile() {
        narrativeOperations.newNarrative();
        controllerOperations.clearGraph();
        sequence.clearHistory();
        dirtyFlag = false;
    }

    public Generator generator() {
        return generator;
    }

    public class Generator {

        /**
         * Returns operation to add a property
         * 
         * @param s
         *            Property Name
         * @return
         * @throws IllegalOperationException
         */
        public Operation addProperty(String s) {
            class AddPropertyOperation implements Operation {
                FXMLPropertyController prop;
                BaseBundle b;

                public AddPropertyOperation(String s) {
                    prop = controllerOperations.createProperty(s);
                    b = new BaseBundle();
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.addProperty(prop.getName(), b);
                    controllerOperations.addProperty(prop);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.removeProperty(prop.getName());
                    controllerOperations.removeProperty(prop);
                }

            }

            return new AddPropertyOperation(s);
        }

        /**
         * Returns operation to remove a property
         * 
         * @param s
         * @throws IllegalOperationException
         */
        public Operation removeProperty(FXMLPropertyController prop) throws IllegalOperationException {
            class RemovePropertyOperation implements Operation {
                FXMLPropertyController prop;
                BaseBundle b;

                public RemovePropertyOperation(FXMLPropertyController prop) throws IllegalOperationException {
                    this.prop = prop;
                    b = narrativeOperations.getPropertyBundle(prop.getName());
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.removeProperty(prop.getName());
                    controllerOperations.removeProperty(prop);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.addProperty(prop.getName(), b);
                    controllerOperations.addProperty(prop);
                }

            }

            return new RemovePropertyOperation(prop);
        }

        public Operation setPropertyTypeSolo(String prop, String oldType, String type, FXMLPropertyController propCont)
                throws IllegalOperationException {
            class SetPropertyTypeOperation implements Operation {

                public SetPropertyTypeOperation() throws IllegalOperationException {
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.setPropertyType(prop, type);

                    propCont.setType(type);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.setPropertyType(prop, oldType);

                    propCont.setType(oldType);
                }

            }
            return new SetPropertyTypeOperation();
        }

        public Operation setPropertyType(String prop, String type, FXMLPropertyController propCont)
                throws IllegalOperationException {
            ArrayList<OperationGenerator> r = new ArrayList<>();
            BaseBundle b = narrativeOperations.getNarrative().getPropertyMapping().get(prop);
            String oldType = narrativeOperations.getPropertyType(prop);
            if (b == null) {
                throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, prop);
            }
            for (String s : b.keySet()) {
                if (!narrativeOperations.isCorrectForType(type, s)) {
                    r.add(() -> removeValue(prop, oldType, s, propCont));
                }
            }
            r.add(() -> setPropertyTypeSolo(prop, oldType, type, propCont));
            if (type.equals(Strings.TYPE_BOOLEAN)) {
                if (!b.containsKey("true")) {
                    r.add(() -> addValue(prop, type, "true", 0, propCont));
                }
                if (!b.containsKey("false")) {
                    r.add(() -> addValue(prop, type, "false", 1, propCont));
                }
            }
            return new CompositeOperation(r);
        }

        public Operation renameProperty(String prop, String newName, FXMLPropertyController propCont) {
            class RenamePropertyAction implements Operation {
                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.renameProperty(prop, newName);
                    propCont.setName(newName);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.renameProperty(newName, prop);
                    propCont.setName(prop);
                }
            }
            return new RenamePropertyAction();
        }

        public Operation removeValue(String prop, String type, String value, FXMLPropertyController propCont)
                throws IllegalOperationException {
            class RemovePropertyAction implements Operation {
                int i = propCont.getIndexOf(value);
                Color c;

                public RemovePropertyAction() throws IllegalOperationException {
                    c = narrativeOperations.getColor(prop, value);
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.removeValue(prop, type, value);
                    propCont.removeValue(value);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.addValue(prop, type, value);
                    narrativeOperations.setColor(prop, value, c);
                    propCont.addValue(value, i);
                }
            }
            return new RemovePropertyAction();
        }

        public Operation addValue(String prop, String type, String value, int index, FXMLPropertyController propCont) {
            class AddPropertyAction implements Operation {
                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.addValue(prop, type, value);
                    propCont.addValue(value, index);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.removeValue(prop, type, value);
                    propCont.removeValue(value);
                }
            }
            return new AddPropertyAction();
        }

        public Operation renameValue(String prop, String type, String value, String newValue,
                FXMLPropertyController propCont) throws IllegalOperationException {
            int i = propCont.getIndexOf(value);
            ArrayList<OperationGenerator> r = new ArrayList<>();
            Color c = narrativeOperations.getColor(prop, value);
            if (!narrativeOperations.isCorrectForType(type, value)) {
                throw new IllegalOperationException(Strings.CANNOT_FORMAT, value);
            }
            BaseBundle b = narrativeOperations.getPropertyBundle(prop);
            if (b == null) {
                throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, prop);
            }
            if (b.containsKey(newValue)) {
                throw new IllegalOperationException(Strings.ITEM_ALREADY_EXISTS, newValue);
            }
            r.add(() -> removeValue(prop, type, value, propCont));
            r.add(() -> addValue(prop, type, newValue, i, propCont));
            r.add(() -> setColor(prop, newValue, c, propCont));
            return new CompositeOperation(r);
        }

        public Operation setColor(String prop, String value, Color col, FXMLPropertyController propCont)
                throws IllegalOperationException {
            class SetColorOperation implements Operation {
                Color old;

                public SetColorOperation(String prop, String value, Color col) throws IllegalOperationException {
                    old = narrativeOperations.getColor(prop, value);
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.setColor(prop, value, col);
                    propCont.recolour(value, col);
                    controller.getGraph().update();
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.setColor(prop, value, old);
                    propCont.recolour(value, old);
                    controller.getGraph().update();
                }
            }
            return new SetColorOperation(prop, value, col);
        }

        public Operation setRouteType(String prop, boolean value, CheckBox check) {
            class SetRouteTypeOperation implements Operation {

                @Override
                public void execute() throws IllegalOperationException {
                    if (value) {
                        narrativeOperations.setAsRouteType(prop);
                    } else {
                        narrativeOperations.clearAsRouteType(prop);
                    }
                    check.setSelected(value);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    if (!value) {
                        narrativeOperations.setAsRouteType(prop);
                    } else {
                        narrativeOperations.clearAsRouteType(prop);
                    }
                    check.setSelected(!value);
                }

            }
            return new SetRouteTypeOperation();
        }

        public Operation addSynchNode(String s, Double x, Double y) throws IllegalOperationException {
            class AddSynchNodeOperation implements Operation {

                GraphNode node;

                public AddSynchNodeOperation(String name, Double x, Double y) throws IllegalOperationException {
                    node = controllerOperations.createSynchNode(name, x, y, controller.getGraph());
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.addSynchNode(node.getName(), node.getX(), node.getY());
                    controllerOperations.addNode(node);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.deleteNode(node.getName());
                    controllerOperations.removeNode(node);
                }
            }
            return new AddSynchNodeOperation(s, x, y);
        }

        public Operation addChoiceNodeSolo(String s, Double x, Double y) throws IllegalOperationException {
            class AddChoiceNodeOperation implements Operation {

                GraphNode node;

                public AddChoiceNodeOperation(String name, Double x, Double y) throws IllegalOperationException {
                    node = controllerOperations.createChoiceNode(name, x, y, controller.getGraph());
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.addChoiceNode(node.getName(), node.getX(), node.getY());
                    controllerOperations.addNode(node);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.deleteNode(node.getName());
                    controllerOperations.removeNode(node);
                }
            }
            return new AddChoiceNodeOperation(s, x, y);
        }

        public Operation addChoiceNode(String s, GraphEdge split) throws IllegalOperationException {
            String route2 = narrativeOperations.getUniqueRouteName();
            ArrayList<OperationGenerator> r = new ArrayList<>();
            double x = split.getXOff();
            double y = split.getYOff();
            r.add(() -> addChoiceNodeSolo(s, split.getControl().getLayoutX(), split.getControl().getLayoutY()));
            r.add(() -> addRoute(route2, s, split.getTo().getName()));
            r.add(() -> setEnd(split.getName(), s));
            r.add(() -> translateRoute(split.getName(), -x, -y));
            return new CompositeOperation(r);
        }

        public Operation removeNodeSolo(String name) throws IllegalOperationException {
            class RemoveNodeOperation implements Operation {

                GraphNode node;
                boolean isChoice;
                BaseBundle props;

                public RemoveNodeOperation(String name) throws IllegalOperationException {
                    this.node = controller.getGraph().getNodes().get(name);
                    if (node == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, name);
                    }
                    try {
                        props = narrativeOperations.getNarrative().getNodeProperties(name);
                    } catch (NullPointerException npe) {
                        throw new IllegalOperationException(npe, Strings.ITEM_DOES_NOT_EXIST, name);
                    }
                    isChoice = narrativeOperations.isChoiceNode(node.getName());
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.deleteNode(node.getName());
                    controllerOperations.removeNode(node);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    if (isChoice)
                        narrativeOperations.addChoiceNode(node.getName(), node.getX(), node.getY());
                    else
                        narrativeOperations.addSynchNode(node.getName(), node.getX(), node.getY());
                    if (props != null) {
                        narrativeOperations.getNarrative().getNode(node.getName()).assignProperties(props);
                    }
                    controllerOperations.addNode(node);
                }

            }
            return new RemoveNodeOperation(name);
        }

        public Operation removeNode(String name) throws IllegalOperationException {
            ArrayList<OperationGenerator> items = new ArrayList<>();
            ArrayList<Route> routes = new ArrayList<>();
            try {
                routes.addAll(narrativeOperations.getNarrative().getNode(name).getEntering());
                routes.addAll(narrativeOperations.getNarrative().getNode(name).getExiting());
            } catch (NullPointerException npe) {
                throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, name);
            }
            for (Route r : routes) {
                items.add(() -> removeRoute(r.getId()));
            }
            items.add(() -> removeNodeSolo(name));
            return new CompositeOperation(items);
        }

        public Operation addRoute(String s, String from, String to) throws IllegalOperationException {
            class AddRouteOperation implements Operation {

                GraphEdge edge;

                public AddRouteOperation(String name, String start, String end) throws IllegalOperationException {
                    GraphNode from = controller.getGraph().getNodes().get(start);
                    if (from == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, start);
                    }
                    GraphNode to = controller.getGraph().getNodes().get(end);
                    if (to == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, end);
                    }
                    edge = controllerOperations.createRoute(name, from, to);
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.addRoute(edge.getName(), edge.getFrom().getName(), edge.getTo().getName(),
                            edge.getXOff(), edge.getYOff());
                    controllerOperations.addRoute(edge);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.deleteRoute(edge.getName());
                    controllerOperations.removeRoute(edge);
                }

            }
            return new AddRouteOperation(s, from, to);
        }

        public Operation removeRoute(String s) throws IllegalOperationException {
            class RemoveRouteOperation implements Operation {

                GraphEdge edge;
                BaseBundle props;

                public RemoveRouteOperation(String s) throws IllegalOperationException {
                    edge = controller.getGraph().getEdges().get(s);
                    if (edge == null)
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, s);
                    try {
                        props = narrativeOperations.getNarrative().getRouteProperties(s);
                    } catch (NullPointerException npe) {
                        throw new IllegalOperationException(npe, Strings.ITEM_DOES_NOT_EXIST, s);
                    }
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.deleteRoute(edge.getName());
                    controllerOperations.removeRoute(edge);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.addRoute(edge.getName(), edge.getFrom().getName(), edge.getTo().getName(),
                            edge.getXOff(), edge.getYOff());
                    if (props != null) {
                        narrativeOperations.getNarrative().getRoute(edge.getName()).assignProperties(props);
                    }
                    controllerOperations.addRoute(edge);
                    controller.getGraph().updateEdge(edge);
                }
            }
            return new RemoveRouteOperation(s);
        }

        public Operation swapChoiceOrSynch(String s) throws IllegalOperationException {
            class SwapChoiceOrSynchOperation implements Operation {
                GraphNode node;

                public SwapChoiceOrSynchOperation(String name) throws IllegalOperationException {
                    node = controller.getGraph().getNodes().get(name);
                    if (node == null)
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, name);
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.switchChoiceOrSynch(node.getName());
                    controllerOperations.swapChoiceOrSynch(node);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.switchChoiceOrSynch(node.getName());
                    controllerOperations.swapChoiceOrSynch(node);
                }
            }
            return new SwapChoiceOrSynchOperation(s);
        }

        public Operation translateNode(String s, double x, double y) throws IllegalOperationException {
            class TranslateNodeOperation implements Operation {
                GraphNode node;
                double x, y;

                public TranslateNodeOperation(String s, double x, double y) throws IllegalOperationException {
                    node = controller.getGraph().getNodes().get(s);
                    if (node == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, s);
                    }
                    this.x = x;
                    this.y = y;
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.translateNode(node.getName(), x, y);
                    node.translate(x, y);
                    controller.getGraph().updateNode(node);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.translateNode(node.getName(), -x, -y);
                    node.translate(-x, -y);
                    controller.getGraph().updateNode(node);
                }
            }
            return new TranslateNodeOperation(s, x, y);
        }

        public Operation translateRoute(String s, double x, double y) throws IllegalOperationException {
            class TranslateRouteOperation implements Operation {
                GraphEdge route;
                double x, y;

                public TranslateRouteOperation(String s, double x, double y) throws IllegalOperationException {
                    route = controller.getGraph().getEdges().get(s);
                    if (route == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, s);
                    }
                    this.x = x;
                    this.y = y;
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.translateRoute(route.getName(), x, y);
                    route.translate(x, y);
                    controller.getGraph().updateEdge(route);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.translateRoute(route.getName(), -x, -y);
                    route.translate(-x, -y);
                    controller.getGraph().updateEdge(route);
                }
            }
            return new TranslateRouteOperation(s, x, y);
        }

        public Operation setStart(String name, String start) throws IllegalOperationException {
            class SetStartOperation implements Operation {
                GraphEdge edge;
                GraphNode oldStart;
                GraphNode newStart;

                public SetStartOperation(String name, String start) throws IllegalOperationException {
                    if (name == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, "null");
                    }
                    if (start == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, "null");
                    }
                    edge = controller.getGraph().getEdges().get(name);
                    if (edge == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, name);
                    }
                    oldStart = edge.getFrom();
                    newStart = controller.getGraph().getNodes().get(start);
                    if (newStart == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, start);
                    }
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.setStart(edge.getName(), newStart.getName());
                    controllerOperations.setStart(edge, newStart);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.setStart(edge.getName(), oldStart.getName());
                    controllerOperations.setStart(edge, oldStart);
                }
            }
            return new SetStartOperation(name, start);
        }

        public Operation setEnd(String name, String end) throws IllegalOperationException {
            class SetEndOperation implements Operation {
                GraphEdge edge;
                GraphNode oldEnd;
                GraphNode newEnd;

                public SetEndOperation(String name, String end) throws IllegalOperationException {
                    if (name == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, "null");
                    }
                    if (end == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, "null");
                    }
                    edge = controller.getGraph().getEdges().get(name);
                    if (edge == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, name);
                    }
                    oldEnd = edge.getTo();
                    newEnd = controller.getGraph().getNodes().get(end);
                    if (newEnd == null) {
                        throw new IllegalOperationException(Strings.ITEM_DOES_NOT_EXIST, end);
                    }
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.setEnd(edge.getName(), newEnd.getName());
                    controllerOperations.setEnd(edge, newEnd);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.setEnd(edge.getName(), oldEnd.getName());
                    controllerOperations.setEnd(edge, oldEnd);
                }
            }
            return new SetEndOperation(name, end);
        }

        public Operation renameNode(String node, String name) {
            class RenameNodeOperation implements Operation {
                String to;
                String from;

                public RenameNodeOperation(String node, String name) {
                    to = name;
                    from = node;
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.renameNode(from, to);
                    controllerOperations.renameNode(from, to);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.renameNode(to, from);
                    controllerOperations.renameNode(to, from);
                }
            }
            return new RenameNodeOperation(node, name);
        }

        public Operation renameRoute(String node, String name) {
            class RenameRouteOperation implements Operation {
                String to;
                String from;

                public RenameRouteOperation(String node, String name) {
                    to = name;
                    from = node;
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.renameRoute(from, to);
                    controllerOperations.renameRoute(from, to);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.renameRoute(to, from);
                    controllerOperations.renameRoute(to, from);
                }
            }
            return new RenameRouteOperation(node, name);
        }

        public Operation assignPropertyToNode(String node, String prop, String type, String value) {
            class AssignPropertyToNodeOperation implements Operation {
                String old = null;

                public AssignPropertyToNodeOperation() {
                    if (narrativeOperations.getNarrative().getNodeProperties(node).get(prop) != null) {
                        old = narrativeOperations.getNarrative().getNodeProperties(node).get(prop).toString();
                    }
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.assignPropertyToNode(node, prop, type, value);
                    controllerOperations.updateItem(node);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.assignPropertyToNode(node, prop, type, old);
                    controllerOperations.updateItem(node);
                }

            }
            return new AssignPropertyToNodeOperation();
        }

        public Operation assignPropertyToRoute(String route, String prop, String type, String value) {
            class AssignPropertyToRouteOperation implements Operation {
                String old = null;

                public AssignPropertyToRouteOperation() {
                    if (narrativeOperations.getNarrative().getRouteProperties(route).get(prop) != null) {
                        old = narrativeOperations.getNarrative().getRouteProperties(route).get(prop).toString();
                    }
                }

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.assignPropertyToRoute(route, prop, type, value);
                    controllerOperations.updateItem(route);
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.assignPropertyToRoute(route, prop, type, old);
                    controllerOperations.updateItem(route);
                }

            }
            return new AssignPropertyToRouteOperation();
        }

        public Operation deletePropertyFromNode(String node, String prop, String type) {
            class AssignPropertyToNodeOperation implements Operation {
                String old = narrativeOperations.getNarrative().getNodeProperties(node).get(prop).toString();

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.deleteProperty(node, prop);
                    controllerOperations.updateItem(node);
                    controller.initSelect();
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.assignPropertyToNode(node, prop, type, old);
                    controllerOperations.updateItem(node);
                }

            }
            return new AssignPropertyToNodeOperation();
        }

        public Operation deletePropertyFromRoute(String route, String prop, String type) {
            class AssignPropertyToRouteOperation implements Operation {
                String old = narrativeOperations.getNarrative().getRouteProperties(route).get(prop).toString();

                @Override
                public void execute() throws IllegalOperationException {
                    narrativeOperations.deleteProperty(route, prop);
                    controllerOperations.updateItem(route);
                    controller.initSelect();
                }

                @Override
                public void undo() throws IllegalOperationException {
                    narrativeOperations.assignPropertyToRoute(route, prop, type, old);
                    controllerOperations.updateItem(route);
                }

            }
            return new AssignPropertyToRouteOperation();
        }
    }
}
