package org.vadere.state.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;

import org.jetbrains.annotations.NotNull;
import org.vadere.state.attributes.Attributes;
import org.vadere.state.attributes.scenario.AttributesAgent;
import org.vadere.state.attributes.scenario.AttributesCar;
import org.vadere.state.attributes.scenario.AttributesDynamicElement;
import org.vadere.state.attributes.scenario.AttributesObstacle;
import org.vadere.state.attributes.scenario.AttributesTopography;
import org.vadere.state.util.Views;
import org.vadere.util.geometry.LinkedCellsGrid;
import org.vadere.util.geometry.shapes.IPoint;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.VPolygon;
import org.vadere.util.geometry.shapes.VShape;
import org.vadere.util.logging.Logger;
import org.vadere.util.math.IDistanceFunction;
import org.vadere.util.random.IReachablePointProvider;
import org.vadere.util.random.SimpleReachablePointProvider;

import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@JsonIgnoreProperties(value = {"allOtherAttributes", "obstacleDistanceFunction", "contextId", "reachablePointProvider", "idProvider"})
public class Topography implements DynamicElementMover{

	/** Transient to prevent JSON serialization. */
	private static Logger logger = Logger.getLogger(Topography.class);

	private IDistanceFunction obstacleDistanceFunction;
	private IReachablePointProvider reachablePointProvider;
	/** A possible empty string identifying a context object. */
	private String contextId;

	// TODO [priority=low] [task=feature] magic number, use attributes / parameter?
	/**
	 * Cell size of the internal storage of DynamicElements. Is used in the LinkedCellsGrid.
	 */
	private static final double CELL_SIZE = 2;

	private final AttributesTopography attributes;

	/**
	 * Obstacles of scenario by id. Tree maps ensures same update order during
	 * iteration between frames.
	 */
	private final List<Obstacle> obstacles;
	/**
	 * Sources of scenario by id. Tree maps ensures same update order during
	 * iteration between frames.
	 */
	@JsonView(Views.CacheViewExclude.class) // ignore when determining if floor field cache is valid
	private final List<Source> sources;
	/**
	 * Targets of scenario by id. Tree maps ensures same update order during
	 * iteration between frames.
	 */
	private final LinkedList<Target> targets;
	/**
	 * TargetChangers of scenario
	 */
	@JsonView(Views.CacheViewExclude.class) // ignore when determining if floor field cache is valid
	private final LinkedList<TargetChanger> targetChangers;
	/**
	 * AbsorbingAreas of scenario by id. Tree maps ensures same update order during
	 * iteration between frames.
	 */
	@JsonView(Views.CacheViewExclude.class) // ignore when determining if floor field cache is valid
	private final LinkedList<AbsorbingArea> absorbingAreas;
	/**
	 * MeasurementAreas.
	 */
	@JsonView(Views.CacheViewExclude.class) // ignore when determining if floor field cache is valid
	private final LinkedList<MeasurementArea> measurementAreas;
	/**
	 * List of obstacles used as a boundary for the whole topography.
	 */
	private List<Obstacle> boundaryObstacles;

	private final List<Stairs> stairs;

	private Teleporter teleporter;

	private transient final DynamicElementContainer<Pedestrian> pedestrians;
	private transient final DynamicElementContainer<Car> cars;
	private boolean recomputeCells;

	@JsonView(Views.CacheViewExclude.class) // ignore when determining if floor field cache is valid
	private AttributesAgent attributesPedestrian;
	@JsonView(Views.CacheViewExclude.class) // ignore when determining if floor field cache is valid
	private AttributesCar attributesCar;

	/** Used to get attributes of all scenario elements. */
	private Set<List<? extends ScenarioElement>> allScenarioElements = new HashSet<>(); // will be filled in the constructor
	
	/** Used to store links to all attributes that are not part of scenario elements. */
	private Set<Attributes> allOtherAttributes = new HashSet<>(); // will be filled in the constructor

	/** set dynamicElementIds to values bigger than the biggest initial element to ensure unique ids.**/
	private final AtomicInteger idProvider;

	public Topography(
			AttributesTopography attributes,
			AttributesAgent attributesPedestrian,
			AttributesCar attributesCar) {

		this.attributes = attributes;
		this.attributesPedestrian = attributesPedestrian;
		this.attributesCar = attributesCar;

		allOtherAttributes.add(attributes);
		allOtherAttributes.add(attributesCar);
		allOtherAttributes.add(attributesPedestrian);
		removeNullFromSet(allOtherAttributes);
		// Actually, only attributes, not nulls should be added to this set.
		// But sometimes null is passed as attributes and added to the set,
		// although it is bad practice to pass null in the first place
		// (as constructor argument).

		obstacles = new LinkedList<>();
		stairs = new LinkedList<>();
		sources = new LinkedList<>();
		targets = new LinkedList<>();
		targetChangers = new LinkedList<>();
		absorbingAreas = new LinkedList<>();
		boundaryObstacles = new LinkedList<>();
		measurementAreas = new LinkedList<>();

		allScenarioElements.add(obstacles);
		allScenarioElements.add(stairs);
		allScenarioElements.add(sources);
		allScenarioElements.add(targets);
		allScenarioElements.add(targetChangers);
		allScenarioElements.add(boundaryObstacles);
		allScenarioElements.add(measurementAreas);

		RectangularShape bounds = this.getBounds();

		this.pedestrians = new DynamicElementContainer<>(bounds, CELL_SIZE);
		this.cars = new DynamicElementContainer<>(bounds, CELL_SIZE);
		recomputeCells = false;

		this.obstacleDistanceFunction = point ->  obstacles.stream()
				.map(Obstacle::getShape)
				.map(shape -> shape.distance(point))
				.min(Double::compareTo)
				.orElse(Double.MAX_VALUE);

		// some meaningful default value if used before simulation is started.
		// will be replaced in the preeLoop like the obstacleDistanceFunction
		this.reachablePointProvider = SimpleReachablePointProvider.uniform(
				new Random(42), getBounds(), obstacleDistanceFunction);


		this.idProvider = new AtomicInteger(1);
		this.contextId = "";
	}

	/** Clean up a set by removing {@code null}. */
	private void removeNullFromSet(Set<?> aSet) {
		aSet.remove(null);
	}

	public Topography() {
		this(new AttributesTopography(), new AttributesAgent(), new AttributesCar());
	}

	public Rectangle2D.Double getBounds() {
		return this.attributes.getBounds();
	}

	public double getBoundingBoxWidth() {
		return this.attributes.getBoundingBoxWidth();
	}

	public Target getTarget(int targetId) {
		for (Target target : this.targets) {
			if (target.getId() == targetId) {
				return target;
			}
		}

		return null;
	}

	public TargetChanger getTargetChanger(int targetChangerId) {
		for (TargetChanger targetChanger : this.targetChangers) {
			if (targetChanger.getId() == targetChangerId) {
				return targetChanger;
			}
		}

		return null;
	}

	public AbsorbingArea getAbsorbingArea(int targetId) {
		for (AbsorbingArea absorbingArea : this.absorbingAreas) {
			if (absorbingArea.getId() == targetId) {
				return absorbingArea;
			}
		}

		return null;
	}

	public double distanceToObstacle(@NotNull IPoint point) {
		return this.obstacleDistanceFunction.apply(point);
	}

	public IDistanceFunction getObstacleDistanceFunction() {
			return obstacleDistanceFunction;
	}

	public IReachablePointProvider getReachablePointProvider() {
		return reachablePointProvider;
	}

	public void setReachablePointProvider(@NotNull IReachablePointProvider reachablePointProvider) {
		this.reachablePointProvider = reachablePointProvider;
	}

	public void setObstacleDistanceFunction(@NotNull IDistanceFunction obstacleDistanceFunction) {
		this.obstacleDistanceFunction = obstacleDistanceFunction;
	}

	public boolean containsTarget(final Predicate<Target> targetPredicate) {
		return getTargets().stream().anyMatch(targetPredicate);
	}

	public boolean containsTarget(final Predicate<Target> targetPredicate, final int targetId) {
		return getTargets().stream().filter(t -> t.getId() == targetId).anyMatch(targetPredicate);
	}

	public boolean containsTargetChanger(final Predicate<TargetChanger> targetChangerPredicate) {
		return getTargetChangers().stream().anyMatch(targetChangerPredicate);
	}

	public boolean containsTargetChanger(final Predicate<TargetChanger> targetChangerPredicate, final int targetChangerId) {
		return getTargetChangers().stream().filter(t -> t.getId() == targetChangerId).anyMatch(targetChangerPredicate);
	}

	public boolean containsAbsorbingArea(final Predicate<AbsorbingArea> absorbingAreaPredicate) {
		return getAbsorbingAreas().stream().anyMatch(absorbingAreaPredicate);
	}

	public boolean containsAbsorbingArea(final Predicate<AbsorbingArea> absorbingAreaPredicate, final int absorbingAreaId) {
		return getAbsorbingAreas().stream().filter(t -> t.getId() == absorbingAreaId).anyMatch(absorbingAreaPredicate);
	}

	/**
	 * Returns a list containing Targets with the specific id. This list may be empty.
	 */
	public List<Target> getTargets(final int targetId) {
		return getTargets().stream().filter(t -> t.getId() == targetId).collect(Collectors.toList());
	}

	public Map<Integer, List<VShape>> getTargetShapes() {
		return getTargets().stream()
				.collect(Collectors
						.groupingBy(t -> t.getId(), Collectors
								.mapping(t -> t.getShape(), Collectors
										.toList())));
	}

	public Map<Integer, List<VShape>> getTargetChangerShapes() {
		return getTargetChangers().stream()
				.collect(Collectors
						.groupingBy(t -> t.getId(), Collectors
								.mapping(t -> t.getShape(), Collectors
										.toList())));
	}

	public Map<Integer, List<VShape>> getAbsorbingAreaShapes() {
		return getAbsorbingAreas().stream()
				.collect(Collectors
						.groupingBy(absorbingArea -> absorbingArea.getId(), Collectors
								.mapping(t -> t.getShape(), Collectors
										.toList())));
	}

	@SuppressWarnings("unchecked")
	private <T extends DynamicElement, TAttributes extends AttributesDynamicElement> DynamicElementContainer<T> getContainer(
			Class<? extends T> elementType) {
		if (Car.class.isAssignableFrom(elementType)) {
			return (DynamicElementContainer<T>) cars;
		}
		if (Pedestrian.class.isAssignableFrom(elementType)) {
			return (DynamicElementContainer<T>) pedestrians;
		}
		// TODO [priority=medium] [task=refactoring] this is needed for the SimulationDataWriter. Refactor in the process of refactoring the Writer.
		if (DynamicElement.class.isAssignableFrom(elementType)) {

			DynamicElementContainer result = new DynamicElementContainer<>(this.getBounds(), CELL_SIZE);
			for (Pedestrian ped : pedestrians.getElements()) {
				result.addElement(ped);
			}
			for (Car car : cars.getElements()) {
				result.addElement(car);
			}
			return result;
		}

		throw new IllegalArgumentException("Class " + elementType + " does not have a container.");
	}

	private boolean checkDynamicElementIdExist(int id){
		return pedestrians.idExists(id) || cars.idExists(id);
	}

	public <T extends DynamicElement> LinkedCellsGrid<T> getSpatialMap(Class<T> elementType) {
		return getContainer(elementType).getCellsElements();
	}

	public <T extends DynamicElement> Collection<T> getElements(Class<T> elementType) {
		return getContainer(elementType).getElements();
	}

	public <T extends DynamicElement> T getElement(Class<T> elementType, int id) {
		return getContainer(elementType).getElement(id);
	}

	@Override
	public <T extends DynamicElement> void addElement(T element) {
		((DynamicElementContainer<T>) getContainer(element.getClass())).addElement(element);
	}

	@Override
	public <T extends DynamicElement> void removeElement(T element) {
		((DynamicElementContainer<T>) getContainer(element.getClass())).removeElement(element);
	}

	@Override
	public <T extends DynamicElement> void moveElement(T element, final VPoint oldPosition) {
		((DynamicElementContainer<T>) getContainer(element.getClass())).moveElement(element, oldPosition);
	}

	/**
	 * The counter does not represent the total number of pedestrians. If initial pedestrians exist
	 * @return next free Id for a pedestrian.
	 */
	public int getNextDynamicElementId(){
		int nextId;
		synchronized (idProvider){
			nextId = this.idProvider.get();
			assert !checkDynamicElementIdExist(nextId): "DynamicElementId issued twice!";
			idProvider.incrementAndGet();
		}
		return nextId;
	}

	/**
	 * This is called for initial pedestrians to set their Ids. If the id equals AttributesAgent.ID_NOT_SET (-1)
	 * a real id is used. Otherwise the fixedId value is used.
	 *
	 * @param fixedId	fixedId Id for a pedestrian. If this id is free use it. If not genrate a new one.
	 * @return				free Id. May be requestedId if it was free.
	 */
	public int getNextDynamicElementId(int fixedId){
		assert !checkDynamicElementIdExist(fixedId): "DynamicElementId issued twice!";
		synchronized (idProvider){
			idProvider.set(fixedId + 1);
		}
		return fixedId;
	}

	public void initializeIdProvider() {
		// getInitialElements() not needed here. At this point the initial elements are added.
		ArrayList<ScenarioElement> all = getAllScenarioElements();
		int maxIdUsed  = all.stream().map(ScenarioElement::getId).max(Integer::compareTo).orElse(0);
		synchronized (idProvider){
			this.idProvider.set(maxIdUsed + 1);
			logger.info(String.format("IdProvider initialized. Id starts at value: %d", this.idProvider.get()));
		}
	}

	public boolean isRecomputeCells() {
		return recomputeCells;
	}

	public void setRecomputeCells(boolean recomputeCells) {
		this.recomputeCells = recomputeCells;
	}

	public List<Source> getSources() {
		return sources;
	}

	public List<Target> getTargets() {
		return targets;
	}

	public List<TargetChanger> getTargetChangers() {
		return targetChangers;
	}

	public List<AbsorbingArea> getAbsorbingAreas() {
		return absorbingAreas;
	}

	public List<Obstacle> getObstacles() {
		return obstacles;
	}

	public List<Stairs> getStairs() {
		return stairs;
	}

	public Teleporter getTeleporter() {
		return teleporter;
	}

	public List<MeasurementArea> getMeasurementAreas() {return  measurementAreas; }

	public MeasurementArea getMeasurementArea(int id){
		return measurementAreas.stream().filter(area -> area.getId() == id).findFirst().orElse(null);
	}

	public DynamicElementContainer<Pedestrian> getPedestrianDynamicElements() {
		return pedestrians;
	}

	public DynamicElementContainer<Car> getCarDynamicElements() {
		return cars;
	}

	public void addSource(Source source) {
		this.sources.add(source);
	}

	public void addTarget(Target target) {
		this.targets.add(target);
	}

	public void addTargetChanger(TargetChanger targetChanger) { this.targetChangers.add(targetChanger); }

	public void addAbsorbingArea(AbsorbingArea absorbingArea) {
		this.absorbingAreas.add(absorbingArea);
	}

	public void addObstacle(Obstacle obstacle) {
		this.obstacles.add(obstacle);
	}

	public void addMeasurementArea(MeasurementArea measurementArea){
		this.measurementAreas.add(measurementArea);
	}

	public void addStairs(Stairs stairs) {
		this.stairs.add(stairs);
	}

	public void setTeleporter(Teleporter teleporter) {
		allScenarioElements.remove(this.teleporter); // remove old teleporter

		this.teleporter = teleporter;
		if (teleporter != null)
			allScenarioElements.add(Collections.singletonList(teleporter));
	}

	public <T extends DynamicElement> void addInitialElement(T element) {
		@SuppressWarnings("unchecked") // getContainer returns a correctly parameterized object
		final DynamicElementContainer<T> container = (DynamicElementContainer<T>) getContainer(element.getClass());
		container.addInitialElement(element);
	}

	public <T extends DynamicElement> List<T> getInitialElements(Class<T> elementType) {
		return this.getContainer(elementType).getInitialElements();
	}

	public boolean hasTeleporter() {
		return teleporter != null;
	}

	public AttributesTopography getAttributes() {
		return attributes;
	}

	public AttributesAgent getAttributesPedestrian() {
		return attributesPedestrian;
	}

	public void setAttributesPedestrian(AttributesAgent attributesPedestrian) {
		this.attributesPedestrian = attributesPedestrian;
	}

	public AttributesCar getAttributesCar() {
		return attributesCar;
	}

	public void setAttributesCar(AttributesCar attributesCar) {
		this.attributesCar = attributesCar;
	}

	public <T extends DynamicElement> void addElementRemovedListener(Class<T> elementType,
			DynamicElementRemoveListener<T> listener) {
		getContainer(elementType).addElementRemovedListener(listener);
	}

	public <T extends DynamicElement> void clearListeners(Class<T> elementType) {
		getContainer(elementType).clearListeners();
	}

	public <T extends DynamicElement> void addElementAddedListener(Class<T> elementType,
			DynamicElementAddListener<T> addListener) {
		getContainer(elementType).addElementAddedListener(addListener);
	}

	/**
	 * Adds a given obstacle to the list of obstacles as well as the list of boundary obstacles.
	 * This way, the boundary can both be treated like normal obstacles, but can also be removed for
	 * writing the topography to file.
	 */
	public void addBoundary(Obstacle obstacle) {

		if (obstacle.getId() == Attributes.ID_NOT_SET){
			int nextId = obstacles.stream().map(Obstacle::getId).max(Integer::compareTo).orElse(1) + 1;
			obstacle.setId(nextId);
		}

		this.addObstacle(obstacle);
		this.boundaryObstacles.add(obstacle);
	}

	public List<Obstacle> getBoundaryObstacles() {
		return new ArrayList<>(boundaryObstacles);
	}

	public void removeBoundary() {
		for (Obstacle boundaryObstacle : this.boundaryObstacles) {
			this.obstacles.remove(boundaryObstacle);
		}
		this.boundaryObstacles.clear();
	}

	/**
	 * Call this method to reset the topography to the state before a simulation take place.
	 * After this call all generated boundaries, pedestrians (from source) and all listeners will be
	 * removed.
	 */
	public void reset() {
		removeBoundary();
		pedestrians.clear();
		cars.clear();
		clearListeners(Pedestrian.class);
		clearListeners(Car.class);
	}

	public boolean isBounded() {
		return this.attributes.isBounded();
	}

	/**
	 * Creates a deep copy of the scenario.
	 * 
	 * @deprecated This manual implementation is error-prone. Remove this method
	 *             and use the standard clone instead.
	 */
	@Deprecated
	@Override
	public Topography clone() {
		Topography s = new Topography(this.attributes, this.attributesPedestrian, this.attributesCar);

		for (Obstacle obstacle : this.getObstacles()) {
			if (boundaryObstacles.contains(obstacle))
				s.addBoundary(obstacle.clone());
			else
				s.addObstacle(obstacle.clone());
		}

		// ensure the clone has the same idProvider
		s.idProvider.set(this.idProvider.get());

		for (MeasurementArea measurementArea : this.getMeasurementAreas()){
			s.addMeasurementArea(measurementArea);
		}
		for (Stairs stairs : getStairs()) {
			s.addStairs(stairs.clone());
		}
		for (Target target : getTargets()) {
			s.addTarget(target.clone());
		}
		for (TargetChanger targetChanger : getTargetChangers()) {
			s.addTargetChanger(targetChanger.clone());
		}
		for (AbsorbingArea absorbingArea: getAbsorbingAreas()) {
			s.addAbsorbingArea(absorbingArea.clone());
		}
		for (Source source : getSources()) {
			s.addSource(source.clone());
		}
		for (Pedestrian pedestrian : getElements(Pedestrian.class)) {
			s.addElement(pedestrian);
		}
		for (Pedestrian ped : getInitialElements(Pedestrian.class)) {
			s.addInitialElement(ped);
		}
		for (Car car : getElements(Car.class)) {
			s.addElement(car);
		}
		for (Car car : getInitialElements(Car.class)) {
			s.addInitialElement(car);
		}

		if (hasTeleporter()) {
			s.setTeleporter(teleporter.clone());
		}

		for (DynamicElementAddListener<Pedestrian> pedestrianAddListener : this.pedestrians.getElementAddedListener()) {
			s.addElementAddedListener(Pedestrian.class, pedestrianAddListener);
		}
		for (DynamicElementRemoveListener<Pedestrian> pedestrianRemoveListener : this.pedestrians
				.getElementRemovedListener()) {
			s.addElementRemovedListener(Pedestrian.class, pedestrianRemoveListener);
		}
		for (DynamicElementAddListener<Car> carAddListener : this.cars.getElementAddedListener()) {
			s.addElementAddedListener(Car.class, carAddListener);
		}
		for (DynamicElementRemoveListener<Car> carRemoveListener : this.cars.getElementRemovedListener()) {
			s.addElementRemovedListener(Car.class, carRemoveListener);
		}

		return s;
	}

	public int getNextFreeTargetID() {
		Collections.sort(this.targets);
		return targets.getLast().getId() + 1;
	}

	public int getNearestTarget(VPoint position) {
		double distance = Double.MAX_VALUE;
		double tmpDistance;
		int targetID = -1;

		for (Target target : this.targets) {
			if (!target.isTargetPedestrian()) {
				tmpDistance = target.getShape().distance(position);
				if (tmpDistance < distance) {
					distance = tmpDistance;
					targetID = target.getId();
				}
			}
		}

		return targetID;
	}

	public boolean hasBoundary() {
		return this.boundaryObstacles.size() > 0;
	}

	public void sealAllAttributes() {
		// tried to do this with flatMap -> weird compiler error "cannot infer type arguments ..."
		for (List<? extends ScenarioElement> list : allScenarioElements) {

			// defensive programming:
			if (list == null)
				throw new RuntimeException("scenario elem list is null");
			for (ScenarioElement scenarioElement : list) {
				if (scenarioElement.getAttributes() == null) {
					list.remove(scenarioElement);
					logger.warn("a scenario element has null as attributes: " + scenarioElement);
					// TODO this is an error in a different place and should be fixed!
				}
			}

			list.forEach(se -> se.getAttributes().seal());
		}
		allOtherAttributes.forEach(a -> a.seal());
	}

	public void generateUniqueIdIfNotSet(){
		ArrayList<ScenarioElement> all = getAllScenarioElements();
		all.addAll(getInitialElements(Pedestrian.class)); // add initial pedestrians. At current point they may not be in the list.
		AtomicInteger nextId = new AtomicInteger(all.stream().map(ScenarioElement::getId).max(Integer::compareTo).orElse(0));
		all.stream()
				.filter(s -> s.getId() == Attributes.ID_NOT_SET)
				.forEach(s -> s.setId(nextId.incrementAndGet()));
	}

	public ArrayList<ScenarioElement> getAllScenarioElements(){
		Set<ScenarioElement> all = new HashSet<>();

		all.addAll(obstacles);
		all.addAll(stairs);
		all.addAll(targets);
		all.addAll(targetChangers);
		all.addAll(sources);
		all.addAll(boundaryObstacles);
		all.addAll(measurementAreas);
		all.addAll(absorbingAreas);
		all.addAll(getPedestrianDynamicElements().getElements());
		all.addAll(getPedestrianDynamicElements().getElements());
		all.addAll(getInitialElements(Pedestrian.class));
		all.addAll(getInitialElements(Car.class));

		return new ArrayList<>(all);

	}

	public static Collection<Obstacle> createObstacleBoundary(@NotNull final Topography topography) {
		List<Obstacle> obstacles = new ArrayList<>();
		VPolygon boundary = new VPolygon(topography.getBounds());
		double width = topography.getBoundingBoxWidth();
		Collection<VPolygon> boundingBoxObstacleShapes = boundary.borderAsShapes(width, width / 2.0, 0.0001);

		for (VPolygon obstacleShape : boundingBoxObstacleShapes) {
			AttributesObstacle obstacleAttributes = new AttributesObstacle(
					-1, obstacleShape);
			Obstacle obstacle = new Obstacle(obstacleAttributes);
			obstacles.add(obstacle);
		}

		return obstacles;
	}

	public String getContextId() {
		return contextId;
	}

	public void setContextId(String contextId) {
		this.contextId = contextId;
	}
}
