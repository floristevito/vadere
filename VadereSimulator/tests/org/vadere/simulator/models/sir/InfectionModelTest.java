package org.vadere.simulator.models.sir;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.vadere.state.attributes.Attributes;
import org.vadere.state.attributes.models.AttributesInfectionModel;
import org.vadere.state.attributes.models.InfectionModelSourceParameters;
import org.vadere.state.attributes.scenario.AttributesAerosolCloud;
import org.vadere.state.attributes.scenario.AttributesAgent;
import org.vadere.state.attributes.scenario.AttributesTarget;
import org.vadere.state.health.InfectionStatus;
import org.vadere.state.scenario.AerosolCloud;
import org.vadere.state.scenario.Pedestrian;
import org.vadere.state.scenario.Target;
import org.vadere.state.scenario.Topography;
import org.vadere.util.geometry.shapes.*;

import java.awt.geom.Path2D;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.vadere.simulator.models.sir.InfectionModel.*;
import static org.vadere.state.scenario.AerosolCloud.createTransformedAerosolCloudShape;

public class InfectionModelTest {
    public static double ALLOWED_DOUBLE_TOLERANCE = 10e-3;

    List<Attributes> attributeList;
    InfectionModel infectionModel;

    // member variables defining aerosol cloud shapes
    double area = 1;
    double correspondingCircleRadius = Math.sqrt(area / Math.PI);
    double vertexDistancePolygon = 2 * correspondingCircleRadius + 0.1;
    double vertexDistanceCircle = 2 * correspondingCircleRadius - 0.1;
    VPoint vertex1 = new VPoint(0, 0);
    VPoint vertex2 = new VPoint(vertex1.x + vertexDistancePolygon, vertex1.y);
    VPoint vertex3 = new VPoint(vertex1.x + vertexDistanceCircle, vertex1.y);
    VShape polygon;
    VShape circle1;
    VShape circle2;


    // will run before each test to setup test environment
    @Before
    public void setUp() {
        attributeList = getAttributeList();
        infectionModel = new InfectionModel();

        polygon = createTransformedAerosolCloudShape(vertex1, vertex2, area);
        circle1 = createTransformedAerosolCloudShape(vertex1, vertex3, area);
        circle2 = createTransformedAerosolCloudShape(vertex1, vertex1, area);
    }

    // cleanup test environment
    @After
    public void after() {
        attributeList.clear();
    }

    @Test
    public void testInitializeOk() {
        // initialize must find the AttributesInfectionModel from the  attributeList
        infectionModel.initialize(attributeList,null,null,null);

        AttributesInfectionModel attributesInfectionModel = infectionModel.getAttributesInfectionModel();
        ArrayList<InfectionModelSourceParameters> infectionModelSourceParameters = attributesInfectionModel.getInfectionModelSourceParameters();

        Assert.assertEquals(attributeList.get(0), attributesInfectionModel);
    }

    public List<Attributes> getAttributeList() {
        ArrayList<Attributes> attrList = new ArrayList<>();
        var att = new AttributesInfectionModel();
        // att.setInfectionModelLastUpdateTime(expectedLastUpdateTime);
        attrList.add(att);

        return attrList;
    }

    // ToDo: createTransformedShape should be tested in AerosolCloudTest
    @Test
    public void throwIfTransformedAerosolCloudShapeWithFarDistancedVerticesNotTypeVPolygon() {
        // create any polygon
        Path2D path = new Path2D.Double();
        path.moveTo(0, 0);
        path.lineTo(1, 0);
        path.lineTo(1, 1);
        path.closePath();
        ShapeType expectedType = (new VPolygon(path)).getType();

        Assert.assertEquals(polygon.getType(), expectedType);
    }

    @Test
    public void throwIfTransformedAerosolCloudShapeWithCloseVerticesNotTypeVCircle() {
        ShapeType expectedType = new VCircle(new VPoint(1,1), 1).getType();
        Assert.assertEquals(circle1.getType(), expectedType);
    }

    @Test
    public void throwIfTransformedAerosolCloudShapeWithCoincidentVerticesNotTypeVCircle() {
        ShapeType expectedType = new VCircle(new VPoint(1,1), 1).getType();
        Assert.assertEquals(circle2.getType(), expectedType);
    }

    @Test
    public void throwIfTransformedShapeVPolygonCenterNotBetweenVertices() {
        VPoint center = polygon.getCentroid();

        VPoint expectedCenter = new VPoint((vertex1.x + vertex2.x) / 2.0, (vertex1.y + vertex2.y) / 2.0);
        double xCoordinateDeviation = Math.abs(center.x - expectedCenter.x);
        double yCoordinateDeviation = Math.abs(center.y - expectedCenter.y);

        assertTrue(xCoordinateDeviation <= ALLOWED_DOUBLE_TOLERANCE && yCoordinateDeviation <= ALLOWED_DOUBLE_TOLERANCE);
    }

    @Test
    public void throwIfTransformedShapeVCircleCenterNotBetweenVertices() {
        VPoint center = circle1.getCentroid();

        VPoint expectedCenter = new VPoint((vertex1.x + vertex3.x) / 2.0, (vertex1.y + vertex3.y) / 2.0);
        double xDeviation = Math.abs(center.x - expectedCenter.x);
        double yDeviation = Math.abs(center.y - expectedCenter.y);
        double xyDeviation = Math.sqrt(xDeviation * xDeviation + yDeviation * yDeviation);

        assertEquals(xyDeviation, 0.0, ALLOWED_DOUBLE_TOLERANCE);
    }

    @Test
    public void circularTransformedAerosolCloudShapeDoesNotMatchRequiredArea() {
        double width = circle1.getBounds2D().getFrame().getWidth();
        double height = circle1.getBounds2D().getFrame().getHeight();
        double actualArea = 1.0 / 4.0 * width * height * Math.PI;

        assertEquals(actualArea, area, ALLOWED_DOUBLE_TOLERANCE);
    }

    @Test
    public void testGetInfectedPedestrians() {
        Topography topography = new Topography();
        createPedestrian(topography, new VPoint(1, 1), 2, 1, InfectionStatus.SUSCEPTIBLE);
        createPedestrian(topography, new VPoint(2, 1), 3, 1, InfectionStatus.INFECTIOUS);
        createPedestrian(topography, new VPoint(3, 1), 4, 1, InfectionStatus.INFECTIOUS);
        Collection<Pedestrian> infectedPedestrians = getInfectedPedestrians(topography);
        Collection<Pedestrian> expectedInfectedPedestrians = topography.getPedestrianDynamicElements().getElements().stream().filter(p -> p.getId() > 2).collect(Collectors.toSet());

        assertEquals(infectedPedestrians, expectedInfectedPedestrians);
    }

    private void createPedestrian(Topography topography, VPoint pedPosition, int pedId, int targetId, InfectionStatus infectionStatus) {
        Pedestrian pedestrian = new Pedestrian(new AttributesAgent(), new Random(1));
        pedestrian.setPosition(pedPosition);
        pedestrian.setInfectionStatus(infectionStatus);
        pedestrian.setId(pedId);

        LinkedList<Integer> targetsPedestrian = new LinkedList<>();
        targetsPedestrian.add(targetId);
        pedestrian.setTargets(targetsPedestrian);

        topography.addElement(pedestrian);
    }

    private void createTarget(Topography topography, VPoint targetCenter, int id) {
        Target target = new Target(new AttributesTarget());
        target.setShape(new VCircle(targetCenter, 0.5));
        target.getAttributes().setId(id);

        topography.addTarget(target);
    }

    @Test
    public void testUpdateNumberOfGeneratedAerosolClouds() {
        InfectionModel infectionModel = new InfectionModel();
        infectionModel.initialize(attributeList,null,null,null);
        Topography topography = new Topography();
        createPedestrian(topography, new VPoint(10, 10), 1, -1, InfectionStatus.INFECTIOUS);

        double simTimeInSec = 0.0;
        double simTimeStepLength = 0.4;
        double simEndTime = 100;

        while(simTimeInSec <= simEndTime) {
            infectionModel.update(simTimeInSec);
            simTimeInSec =+ simTimeStepLength;
        }

        int expectedNumberOfAerosolClouds = (int) Math.floor(simEndTime / infectionModel.getAttributesInfectionModel().getPedestrianRespiratoryCyclePeriod());
        int actualNumberOfAerosolClouds = topography.getAerosolClouds().size();

        assertEquals(actualNumberOfAerosolClouds, expectedNumberOfAerosolClouds);
    }

    @Test
    public void throwIfPedestrianInsideAerosolCloudNotDetected() {
        Topography topography = new Topography();
        createPedestrian(topography, new VPoint(10, 10), 1, -1, InfectionStatus.SUSCEPTIBLE);
        AerosolCloud aerosolCloud = new AerosolCloud(new AttributesAerosolCloud(new VCircle(new VPoint(10, 10), 1), 0.0));
        topography.addAerosolCloud(aerosolCloud);
        boolean inAerosolCloud = isPedestrianInAerosolCloud(aerosolCloud, topography.getPedestrianDynamicElements().getElement(1));

        assertTrue(inAerosolCloud);
    }

    @Test
    public void throwIfPedestrianOutsideAerosolCloudConsideredInside() {
        Topography topography = new Topography();
        createPedestrian(topography, new VPoint(5, 5), 1, -1, InfectionStatus.SUSCEPTIBLE);
        AerosolCloud aerosolCloud = new AerosolCloud(new AttributesAerosolCloud(new VCircle(new VPoint(10, 10), 1), 0.0));
        topography.addAerosolCloud(aerosolCloud);
        boolean inAerosolCloud = isPedestrianInAerosolCloud(aerosolCloud, topography.getPedestrianDynamicElements().getElement(1));

        assertFalse(inAerosolCloud);
    }

    @Test
    public void testGetPedestriansInsideAerosolCloud() {
        Topography topography = new Topography();
        AerosolCloud aerosolCloud = new AerosolCloud(new AttributesAerosolCloud(new VCircle(new VPoint(10, 10), 1), 0.0));
        aerosolCloud.setId(1);
        topography.addAerosolCloud(aerosolCloud);
        // pedestrians outside cloud
        createPedestrian(topography, new VPoint(12, 12), 2, -1, InfectionStatus.SUSCEPTIBLE);
        createPedestrian(topography, new VPoint(1, 1), 3, -1, InfectionStatus.SUSCEPTIBLE);
        // pedestrians inside cloud
        createPedestrian(topography, new VPoint(10, 10), 4, -1, InfectionStatus.SUSCEPTIBLE);
        createPedestrian(topography, new VPoint(10.5, 10.5), 5, -1, InfectionStatus.SUSCEPTIBLE);

        Collection<Pedestrian> expectedPedestriansInAerosolCloud = new LinkedList<Pedestrian>();
        expectedPedestriansInAerosolCloud.add(topography.getPedestrianDynamicElements().getElement(4));
        expectedPedestriansInAerosolCloud.add(topography.getPedestrianDynamicElements().getElement(5));

        Collection<Pedestrian> actualPedestriansInAerosolCloud = getPedestriansInsideAerosolCloud(topography, aerosolCloud);
        assertEquals(actualPedestriansInAerosolCloud, expectedPedestriansInAerosolCloud);
    }
}