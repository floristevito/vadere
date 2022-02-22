package org.vadere.simulator.models.infection;

import org.vadere.annotation.factories.models.ModelClass;
import org.vadere.simulator.context.VadereContext;
import org.vadere.simulator.control.scenarioelements.SourceController;
import org.vadere.simulator.control.simulation.ControllerProvider;
import org.vadere.simulator.models.Model;
import org.vadere.simulator.projects.Domain;
import org.vadere.state.attributes.Attributes;
import org.vadere.state.attributes.models.AttributesTransmissionModel;
import org.vadere.state.attributes.models.infection.AttributesExposureModelSourceParameters;
import org.vadere.state.attributes.scenario.AttributesAerosolCloud;
import org.vadere.state.attributes.scenario.AttributesAgent;
import org.vadere.state.attributes.scenario.AttributesDroplets;
import org.vadere.state.health.TransmissionModelHealthStatus;
import org.vadere.state.scenario.*;
import org.vadere.util.geometry.shapes.VLine;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.VShape;
import org.vadere.util.geometry.shapes.Vector2D;
import org.vadere.util.logging.Logger;

import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.stream.Collectors;

import static org.vadere.state.scenario.Droplets.createTransformedDropletsShape;

/**
 * This class models the spread of infectious pathogen among pedestrians.
 * For this purpose, the TransmissionModel controls the airborne transmission of pathogen from infectious pedestrians to
 * other pedestrians, i.e. it
 * <ul>
 *     <li>initializes each pedestrian's {@link TransmissionModelHealthStatus} after a pedestrian is inserted into the topography,</li>
 *     <li>updates the pedestrian's {@link TransmissionModelHealthStatus}</li>
 *     <li>creates, updates and deletes each {@link AerosolCloud}</li>
 *     <li>creates, updates and deletes {@link Droplets}</li>
 * </ul>
 */
@ModelClass
public class TransmissionModel extends AbstractExposureModel {

	protected static Logger logger = Logger.getLogger(TransmissionModel.class);

	private AttributesTransmissionModel attrTransmissionModel;
	double simTimeStepLength;
	Topography topography;
	int aerosolCloudIdCounter;

	private Map<Integer, VPoint> lastPedestrianPositions;
	private Map<Integer, Vector2D> viewingDirections;
	private static final double MIN_PED_STEP_LENGTH = 0.1;

	/**
	 * Key that is used for initializeVadereContext in ScenarioRun
	 */
	public static final String simStepLength = "simTimeStepLength";

	/**
	 * constant that results from exponential decay of pathogen concentration: C(t) = C_init * exp(-lambda * t),
	 * lambda = exponentialDecayFactor / halfLife
	 */
	private static final double exponentialDecayFactor = Math.log(2.0);

	/**
	 * minimumPercentage defines a percentage of the initial pathogen concentration
	 * (pathogenLoad / aerosolCloud.volume); As soon as an aerosolCloud has reached the minimum concentration, the
	 * aerosolCloud is considered negligible and therefore deleted
	 */
	private static final double minimumPercentage = 0.01;

	@Override
	public void initialize(List<Attributes> attributesList, Domain domain, AttributesAgent attributesPedestrian, Random random) {
			this.domain = domain;
			this.random = random;
			this.attributesAgent = attributesPedestrian;
			this.attrTransmissionModel = Model.findAttributes(attributesList, AttributesTransmissionModel.class);
			this.topography = domain.getTopography();
			this.simTimeStepLength = VadereContext.get(this.topography).getDouble(simStepLength);
			this.aerosolCloudIdCounter = 1;
			this.viewingDirections = new HashMap<>();
			this.lastPedestrianPositions = new HashMap<>();
	}

	@Override
	public void registerToScenarioElementControllerEvents(ControllerProvider controllerProvider) {
		// ToDo: controllerProvider should be handled by initialize method (this requires changes in all models)
		for (var controller : controllerProvider.getSourceControllers()){
			controller.register(this::sourceControllerEvent);
		}
	}

	@Override
	public void preLoop(double simTimeInSec) {}

	@Override
	public void postLoop(double simTimeInSec) {}

	@Override
	public void update(double simTimeInSec) {

		if (attrTransmissionModel.isAerosolCloudsActive()) {
			executeAerosolCloudEmissionEvents(simTimeInSec);
			updateAerosolClouds(simTimeInSec);
			updatePedestriansExposureToAerosolClouds();
		}

		if (attrTransmissionModel.isDropletsActive()) {
			executeDropletEmissionEvents(simTimeInSec);
			updateDroplets(simTimeInSec);
			updatePedestriansExposureToDroplets();
		}

		if (attrTransmissionModel.isAerosolCloudsActive() || attrTransmissionModel.isDropletsActive()) {
			updatePedsHealthStatus(simTimeInSec);
		}
	}

	@Override
	public void updatePedestrianDegreeOfExposure(Pedestrian pedestrian, double deltaDegreeOfExposure) {
		pedestrian.getHealthStatus(TransmissionModelHealthStatus.class).incrementDegreeOfExposure(deltaDegreeOfExposure);
	}

	public void executeAerosolCloudEmissionEvents(double simTimeInSec) {
		Collection<Pedestrian> infectiousPedestrians = getInfectiousPedestrians(topography);
		for (Pedestrian pedestrian : infectiousPedestrians) {
			createAerosolClouds(simTimeInSec, pedestrian);
		}
	}

	public void executeDropletEmissionEvents(double simTimeInSec) {
		Collection<Pedestrian> infectiousPedestrians = getInfectiousPedestrians(topography);
		for (Pedestrian pedestrian : infectiousPedestrians) {
			createDroplets(simTimeInSec, pedestrian);
		}
	}

	public void updateAerosolClouds(double simTimeInSec) {
		updateAerosolCloudsPathogenLoad(simTimeInSec);
		updateAerosolCloudsExtent();
		deleteExpiredAerosolClouds();
	}

	public void updateDroplets(double simTimeInSec) {
		// dropletsPathogenLoad remains unchanged until deletion
		deleteExpiredDroplets(simTimeInSec);
	}

	public void createAerosolClouds(double simTimeInSec, Pedestrian pedestrian) {

		if (pedestrian.getHealthStatus(TransmissionModelHealthStatus.class).isStartingExhalation()) {
			pedestrian.getHealthStatus(TransmissionModelHealthStatus.class).setExhalationStartPosition(pedestrian.getPosition());

		} else if (pedestrian.getHealthStatus(TransmissionModelHealthStatus.class).isStartingInhalation()) {
			VPoint startBreatheOutPosition = pedestrian.getHealthStatus(TransmissionModelHealthStatus.class).getExhalationStartPosition();
			VPoint stopBreatheOutPosition = pedestrian.getPosition();
			VLine distanceWalkedDuringExhalation = new VLine(startBreatheOutPosition, stopBreatheOutPosition);

			AerosolCloud aerosolCloud = generateAerosolCloud(simTimeInSec, distanceWalkedDuringExhalation);
			topography.addAerosolCloud(aerosolCloud);

			pedestrian.getHealthStatus(TransmissionModelHealthStatus.class).resetStartExhalationPosition();
		}
	}

	private AerosolCloud generateAerosolCloud(double simTimeInSec, VLine distanceWalkedDuringExhalation) {
		VPoint center = distanceWalkedDuringExhalation.midPoint();

		AerosolCloud aerosolCloud = new AerosolCloud(new AttributesAerosolCloud(aerosolCloudIdCounter,
				attrTransmissionModel.getAerosolCloudInitialRadius(),
				center,
				simTimeInSec,
				attrTransmissionModel.getAerosolCloudInitialPathogenLoad(),
				attrTransmissionModel.getAerosolCloudInitialPathogenLoad()));

		aerosolCloudIdCounter = aerosolCloudIdCounter + 1;

		return aerosolCloud;
	}

	private void createDroplets(double simTimeInSec, Pedestrian pedestrian) {
		// ToDo: refactor: it could be better to have the walking directions stored in pedestrian
		int pedestrianId = pedestrian.getId();
		Vector2D viewingDirection;
		VPoint currentPosition = pedestrian.getPosition();
		VPoint lastPosition = lastPedestrianPositions.get(pedestrianId);
		if (lastPedestrianPositions.get(pedestrianId) == null) {
			viewingDirection = new Vector2D(Math.random(), Math.random());
		} else {
			if (lastPosition.distance(currentPosition) < MIN_PED_STEP_LENGTH) {
				viewingDirection = viewingDirections.get(pedestrianId);
			} else {
				viewingDirection = new Vector2D(currentPosition.getX() - lastPosition.getX(),
						currentPosition.getY() - lastPosition.getY());
			}
		}
		viewingDirection.normalize(1);
		viewingDirections.put(pedestrianId, viewingDirection);
		lastPedestrianPositions.put(pedestrianId, currentPosition);

		// period between two droplet generating respiratory events
		double dropletExhalationPeriod = 1 / attrTransmissionModel.getDropletsEmissionFrequency();

		if (simTimeInSec % dropletExhalationPeriod < simTimeStepLength) {

			VShape shape = createTransformedDropletsShape(pedestrian.getPosition(),
					viewingDirection,
					attrTransmissionModel.getDropletsDistanceOfSpread(),
					Math.toRadians(attrTransmissionModel.getDropletsAngleOfSpreadInDeg()));

			Droplets droplets = new Droplets(new AttributesDroplets(1,
					shape,
					simTimeInSec,
					attrTransmissionModel.getDropletsPathogenLoad()));

			topography.addDroplets(droplets);
		}
	}

	//TODO define recursive; then, if possible, remove property initialPathogenLoad from AerosolCloud
	public void updateAerosolCloudsPathogenLoad(double simTimeInSec) {
		double lambda = exponentialDecayFactor / attrTransmissionModel.getAerosolCloudHalfLife();

		Collection<AerosolCloud> allAerosolClouds = topography.getAerosolClouds();
		for (AerosolCloud aerosolCloud : allAerosolClouds) {
			double t = simTimeInSec - aerosolCloud.getCreationTime();
			aerosolCloud.setCurrentPathogenLoad(aerosolCloud.getInitialPathogenLoad() * Math.exp(-lambda * t));
		}
	}

	public void updateAerosolCloudsExtent() {
		Collection<AerosolCloud> allAerosolClouds = topography.getAerosolClouds();
		for (AerosolCloud aerosolCloud : allAerosolClouds) {
			double deltaRadius = 0.0;

			// Increasing extent due to dispersion, multiplication with simTimeStepLength keeps deltaRadius independent of simulation step width
			if (attrTransmissionModel.getAerosolCloudAirDispersionFactor() > 0) {
				deltaRadius = attrTransmissionModel.getAerosolCloudAirDispersionFactor() * simTimeStepLength;
			}

			// Increasing extent due to moving air caused by agents, multiplication with simTimeStepLength keeps deltaRadius independent of simulation step width
			if (attrTransmissionModel.getAerosolCloudPedestrianDispersionWeight() > 0) {
				Collection<Pedestrian> pedestriansInsideCloud = getPedestriansInsideAerosolCloud(topography, aerosolCloud);
			for (Pedestrian pedestrian : pedestriansInsideCloud) {
				deltaRadius += pedestrian.getVelocity().getLength() * attrTransmissionModel.getAerosolCloudPedestrianDispersionWeight() * simTimeStepLength;
			}
		}

			aerosolCloud.increaseShape(deltaRadius);
		}
	}

	/**
	 * Deletes aerosol clouds with negligible pathogen concentration, i.e. if current pathogen concentration is smaller
	 * than a threshold (minimumPercentage * initial pathogen concentration)
	 */
	public void deleteExpiredAerosolClouds() {

		double initialCloudVolume = AerosolCloud.radiusToVolume(attrTransmissionModel.getAerosolCloudInitialRadius());
		double initialPathogenConcentration = attrTransmissionModel.getAerosolCloudInitialPathogenLoad() / initialCloudVolume;
		double minimumConcentration = minimumPercentage * initialPathogenConcentration;

		Collection<AerosolCloud> aerosolCloudsToBeDeleted = topography.getAerosolClouds()
				.stream()
				.filter(a -> a.getPathogenConcentration() < minimumConcentration)
				.collect(Collectors.toSet());
		for (AerosolCloud aerosolCloud : aerosolCloudsToBeDeleted) {
			topography.getAerosolClouds().remove(aerosolCloud);
		}
	}

	public void deleteExpiredDroplets(double simTimeInSec) {
		Collection<Droplets> dropletsToBeDeleted = topography.getDroplets()
				.stream()
				.filter(d -> attrTransmissionModel.getDropletsLifeTime() + d.getCreationTime() < simTimeInSec)
				.collect(Collectors.toSet());
		for (Droplets droplets : dropletsToBeDeleted) {
			topography.getDroplets().remove(droplets);
		}
	}

	private void updatePedsHealthStatus(double simTimeInSec) {
		Collection<Pedestrian> allPedestrians = topography.getPedestrianDynamicElements().getElements();
		for (Pedestrian pedestrian : allPedestrians) {
			pedestrian.getHealthStatus(TransmissionModelHealthStatus.class).updateRespiratoryCycle(simTimeInSec, attrTransmissionModel.getPedestrianRespiratoryCyclePeriod());
		}
	}

	private void updatePedestriansExposureToAerosolClouds() {
		Collection<Pedestrian> breathingInPeds = topography.getPedestrianDynamicElements()
				.getElements()
				.stream()
				.filter(p -> p.getHealthStatus(TransmissionModelHealthStatus.class).isBreathingIn())
				.collect(Collectors.toSet());

		// Agents absorb pathogen continuously but simulation is discrete. Therefore, the absorption during inhalation
		// must be divided into absorption for each sim step:
		double inhalationPeriodLength = attrTransmissionModel.getPedestrianRespiratoryCyclePeriod() / 2.0;
		double aerosolAbsorptionRatePerSimStep = attrTransmissionModel.getAerosolCloudAbsorptionRate() * (simTimeStepLength / inhalationPeriodLength);

		Collection<AerosolCloud> allAerosolClouds = topography.getAerosolClouds();
		for (AerosolCloud aerosolCloud : allAerosolClouds) {
			Collection<Pedestrian> breathingInPedsInAerosolCloud = breathingInPeds
					.stream()
					.filter(p -> aerosolCloud.getShape().contains(p.getPosition()))
					.collect(Collectors.toSet());

			for (Pedestrian ped : breathingInPedsInAerosolCloud) {
				double deltaDegreeOfExposure = aerosolCloud.getPathogenConcentration() * aerosolAbsorptionRatePerSimStep;
				updatePedestrianDegreeOfExposure(ped, deltaDegreeOfExposure);
			}
		}
	}

	private void updatePedestriansExposureToDroplets() {
		Collection<Pedestrian> breathingInPeds = topography.getPedestrianDynamicElements()
				.getElements()
				.stream()
				.filter(p -> p.getHealthStatus(TransmissionModelHealthStatus.class).isBreathingIn())
				.collect(Collectors.toSet());

		/*
		 * Agents absorb pathogen continuously but simulation is discrete. Therefore, the absorption during inhalation
		 * must be divided into absorption for each sim step:
		 */
		double inhalationPeriodLength = attrTransmissionModel.getPedestrianRespiratoryCyclePeriod() / 2.0;
		double dropletsAbsorptionRatePerSimStep = attrTransmissionModel.getDropletsAbsorptionRate() * (simTimeStepLength / inhalationPeriodLength);

		/*
		 * Intake of droplets: Inhaling agents simply absorb a fraction of the pathogen from droplets they are exposed
		 * to. In contrast to intake of pathogen from aerosol clouds, we do not consider concentrations (for simplicity
		 * or to avoid further assumptions on pathogen distribution within droplets).
		 */
		Collection<Droplets> allDroplets = topography.getDroplets();
		for (Droplets droplets : allDroplets) {
			Collection<Pedestrian> breathingInPedsInDroplets = breathingInPeds
					.stream()
					.filter(p -> droplets.getShape().contains(p.getPosition()))
					.collect(Collectors.toSet());

			for (Pedestrian ped : breathingInPedsInDroplets) {
				double deltaDegreeOfExposure = attrTransmissionModel.getDropletsPathogenLoad() * dropletsAbsorptionRatePerSimStep;
				updatePedestrianDegreeOfExposure(ped, deltaDegreeOfExposure);
			}
		}
	}

	public Collection<Pedestrian> getInfectiousPedestrians(Topography topography) {
		return topography.getPedestrianDynamicElements()
				.getElements()
				.stream()
				.filter(Pedestrian::isInfectious)
				.collect(Collectors.toSet());
	}

	public Agent sourceControllerEvent(SourceController controller, double simTimeInSec, Agent scenarioElement) {
		// SourceControllerListener. This will be called  *after* a pedestrian is inserted into the
		// topography by the given SourceController. Change model state on Agent here
		AttributesExposureModelSourceParameters sourceParameters = defineSourceParameters(controller);

		Pedestrian ped = (Pedestrian) scenarioElement;
		ped.addHealthStatus(TransmissionModelHealthStatus.class);
		ped.setInfectious(sourceParameters.isInfectious());
		ped.setDegreeOfExposure(0);
		ped.getHealthStatus(TransmissionModelHealthStatus.class).setRespiratoryTimeOffset(random.nextDouble() * attrTransmissionModel.getPedestrianRespiratoryCyclePeriod());
		ped.getHealthStatus(TransmissionModelHealthStatus.class).setBreathingIn(false);
		//TODO check exhalation start position null?

		logger.infof(">>>>>>>>>>>sourceControllerEvent at time: %f  agentId: %d", simTimeInSec, scenarioElement.getId());
		return ped;
	}

	private AttributesExposureModelSourceParameters defineSourceParameters(SourceController controller) {
		int sourceId = controller.getSourceId();
		int defaultSourceId = -1;
		Optional<AttributesExposureModelSourceParameters> sourceParameters = attrTransmissionModel
				.getTransmissionModelSourceParameters().stream().filter(s -> s.getSourceId() == sourceId).findFirst();

		// if sourceId not set by user, check if the user has defined default attributes by setting sourceId = -1
		if (sourceParameters.isEmpty()) {
			sourceParameters = attrTransmissionModel.getTransmissionModelSourceParameters().stream().filter(s -> s.getSourceId() == defaultSourceId).findFirst();

			// if no user defined default values: use attributesTransmissionModel default values
			if (sourceParameters.isPresent()) {
				logger.infof(">>>>>>>>>>>defineSourceParameters: sourceId %d not set explicitly transmissionModelSourceParameters. Source uses default transmissionModelSourceParameters defined for sourceId: %d", sourceId, defaultSourceId);
			} else {
				logger.errorf(">>>>>>>>>>>defineSourceParameters: sourceId %d is not set in transmissionModelSourceParameters", sourceId);
			}
		}
			return sourceParameters.get();
	}

	public AttributesTransmissionModel getAttributesTransmissionModel() {
		return attrTransmissionModel;
	}

	public static Collection<Pedestrian> getDynamicElementsNearAerosolCloud(Topography topography, AerosolCloud aerosolCloud) {
		final Rectangle2D aerosolCloudBounds = aerosolCloud.getShape().getBounds2D();
		final VPoint centerOfAerosolCloud = new VPoint(aerosolCloudBounds.getCenterX(), aerosolCloudBounds.getCenterY());

		final double aerosolCloudProximity = Math.max(aerosolCloudBounds.getHeight(), aerosolCloudBounds.getWidth());

		return topography.getSpatialMap(Pedestrian.class).getObjects(centerOfAerosolCloud, aerosolCloudProximity);
	}

	public static boolean isPedestrianInAerosolCloud(AerosolCloud aerosolCloud, Pedestrian pedestrian) {
		VShape aerosolCloudShape = aerosolCloud.getShape();
		VPoint pedestrianPosition = pedestrian.getPosition();
		return aerosolCloudShape.contains(pedestrianPosition);
	}

	public static Collection<Pedestrian> getPedestriansInsideAerosolCloud(Topography topography, AerosolCloud aerosolCloud) {
		Collection<Pedestrian> pedestriansInsideAerosolCloud = new LinkedList<>();

		Collection<Pedestrian> pedestriansNearAerosolCloud = getDynamicElementsNearAerosolCloud(topography, aerosolCloud);
		for (Pedestrian pedestrian : pedestriansNearAerosolCloud) {
			if (isPedestrianInAerosolCloud(aerosolCloud, pedestrian)){
				pedestriansInsideAerosolCloud.add(pedestrian);
			}
		}
		return pedestriansInsideAerosolCloud;
	}

}
