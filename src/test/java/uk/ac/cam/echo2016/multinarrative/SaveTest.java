package uk.ac.cam.echo2016.multinarrative;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import uk.ac.cam.echo2016.multinarrative.io.SaveReader;
import uk.ac.cam.echo2016.multinarrative.io.SaveWriter;

import java.io.IOException;


public class SaveTest {

    GUINarrative gNarr;

    //Taken from GUINarrativeTest
    @Before
    public void setup() throws IOException {
        gNarr = new GUINarrative();

        SynchronizationNode s1 = new SynchronizationNode("start");
        ChoiceNode c1 = new ChoiceNode("choice1");
        SynchronizationNode s2 = new SynchronizationNode("left");
        SynchronizationNode s3 = new SynchronizationNode("right");
        SynchronizationNode s4 = new SynchronizationNode("end");
        Route l1 = new Route("link1", "Chris", s1, c1);
        Route l2 = new Route("link2", "Chris", c1, s2);
        Route l3 = new Route("link3", "Chris", c1, s3);
        Route l4 = new Route("link4", "Chris", s2, s4);
        Route l5 = new Route("link5", "Chris", s3, s4);
        gNarr.addNode(s1);
        gNarr.addNode(c1);
        gNarr.addNode(s2);
        gNarr.addNode(s3);
        gNarr.addNode(s4);
        gNarr.addRoute(l1);
        gNarr.addRoute(l2);
        gNarr.addRoute(l3);
        gNarr.addRoute(l4);
        gNarr.addRoute(l5);
        gNarr.start = s1;

        SaveWriter.saveObject("test.gui",gNarr);
        gNarr = null;
        gNarr = SaveReader.loadGUINarrative("test.gui");
    }

    @Test
    public void structureTest() {
        assertEquals("Check root is correct", "start", gNarr.start.getId());
        assertEquals("Check root has correct exit route", "link1", gNarr.start.getOptions().get(0).getId());
        Node c1 = gNarr.start.getOptions().get(0).getEnd();
        assertEquals("Check child of link1", "choice1", c1.getId());
        assertEquals("Check choice1 has correct number of children", 2, c1.getOptions().size());
        assertEquals("Check choice1 has correct left route", "link2", c1.getOptions().get(0).getId());
        assertEquals("Check choice1 has correct right route", "link3", c1.getOptions().get(1).getId());
        Node left = c1.getOptions().get(0).getEnd();
        Node right = c1.getOptions().get(1).getEnd();
        assertEquals("Check choice1 has correct left child", "left", left.getId());
        assertEquals("Check choice1 has correct right child", "right", right.getId());
        assertEquals("Check left has correct child route", "link4", left.getOptions().get(0).getId());
        assertEquals("Check right has correct child route", "link5", right.getOptions().get(0).getId());
        assertEquals("Check left leads into end node", "end", left.getOptions().get(0).getEnd().getId());
        assertEquals("Check right leads into end node", "end", right.getOptions().get(0).getEnd().getId());

    }
}
