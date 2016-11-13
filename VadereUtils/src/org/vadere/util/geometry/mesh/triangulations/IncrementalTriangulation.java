package org.vadere.util.geometry.mesh.triangulations;

import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.vadere.util.geometry.mesh.DAG;
import org.vadere.util.geometry.mesh.DAGElement;
import org.vadere.util.geometry.mesh.impl.PFace;
import org.vadere.util.geometry.mesh.iterators.FaceIterator;
import org.vadere.util.geometry.mesh.inter.IFace;
import org.vadere.util.geometry.mesh.inter.IHalfEdge;
import org.vadere.util.geometry.mesh.inter.IMesh;
import org.vadere.util.geometry.mesh.iterators.AdjacentFaceIterator;
import org.vadere.util.geometry.mesh.impl.PHalfEdge;
import org.vadere.util.geometry.GeometryUtils;
import org.vadere.util.geometry.mesh.impl.PMesh;
import org.vadere.util.geometry.shapes.IPoint;
import org.vadere.util.geometry.shapes.VCircle;
import org.vadere.util.geometry.shapes.VLine;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.VRectangle;
import org.vadere.util.geometry.shapes.VTriangle;
import org.vadere.util.triangulation.BowyerWatsonSlow;
import org.vadere.util.triangulation.IPointConstructor;
import org.vadere.util.triangulation.ITriangulation;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.swing.*;

public class IncrementalTriangulation<P extends IPoint, E extends IHalfEdge<P>, F extends IFace<P>> implements ITriangulation<P, E, F> {

	protected Set<P> points;
	protected final IPointConstructor<P> pointConstructor;

	// TODO: use symbolic for the super-triangle points instead of real points!
	private P p0;
	private P p1;
	private P p2;
	private E he0;
	private E he1;
	private E he2;
	private P p_max;
	private P p_min;
	private boolean finalized;
	private IMesh<P, E, F> mesh;

	private DAG<DAGElement<P, F>> dag;
	private final HashMap<F, DAG<DAGElement<P, F>>> map;
	private double eps = 0.0000001;
	protected F superTriangle;
	private F borderFace;
	private final Predicate<E> illegalPredicate;
	private static Logger log = LogManager.getLogger(IncrementalTriangulation.class);

	public IncrementalTriangulation(
			final IMesh<P, E, F> mesh,
			final Set<P> points,
			final IPointConstructor<P> pointConstructor,
			final Predicate<E> illegalPredicate) {
		this.mesh = mesh;
		this.points = points;
		this.illegalPredicate = illegalPredicate;
		this.pointConstructor = pointConstructor;
		this.map = new HashMap<>();
		P p_max = points.parallelStream().reduce(pointConstructor.create(Double.MIN_VALUE, Double.MIN_VALUE), (a, b) -> pointConstructor.create(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY())));
		P p_min = points.parallelStream().reduce(pointConstructor.create(Double.MIN_VALUE, Double.MIN_VALUE), (a, b) -> pointConstructor.create(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY())));
		init(p_max, p_min);
	}

	public IncrementalTriangulation(final IMesh<P, E, F> mesh, final Set<P> points, final IPointConstructor<P> pointConstructor) {
		this(mesh, points, pointConstructor, halfEdge -> true);
	}

	public IncrementalTriangulation(
			final IMesh<P, E, F> mesh,
			final double minX,
			final double minY,
			final double width,
			final double height,
			final IPointConstructor<P> pointConstructor,
			final Predicate<E> illegalPredicate) {
		this.mesh = mesh;
		this.points = new HashSet<>();
		this.pointConstructor = pointConstructor;
		this.map = new HashMap<>();
		this.illegalPredicate = illegalPredicate;
		this.p_max = pointConstructor.create(minX + width, minY + height);
		this.p_min = pointConstructor.create(minX, minY);
		init(p_max, p_min);
	}

	public IncrementalTriangulation(
			final IMesh<P, E, F> mesh,
			final double minX,
			final double minY,
			final double width,
			final double height,
			final IPointConstructor<P> pointConstructor) {
		this(mesh, minX, minY, width, height, pointConstructor, halfEdge -> true);
	}

	private void init(final P p_max, final P p_min) {
		VRectangle bound = new VRectangle(p_min.getX(), p_min.getY(), p_max.getX()-p_min.getX(), p_max.getY()- p_min.getY());

		double gap = 1.0;
		double max = Math.max(bound.getWidth(), bound.getHeight())*2;
		p0 = pointConstructor.create(bound.getX() - max - gap, bound.getY() - gap);
		p1 = pointConstructor.create(bound.getX() + 2 * max + gap, bound.getY() - gap);
		p2 = pointConstructor.create(bound.getX() + (max+2*gap)/2, bound.getY() + 2 * max + gap);

		superTriangle = mesh.createFace(p0, p1, p2);
		borderFace = mesh.getTwinFace(mesh.getEdge(superTriangle));

		List<E> borderEdges = mesh.getEdges(borderFace);
		he0 = borderEdges.get(0);
		he1 = borderEdges.get(1);
		he2 = borderEdges.get(2);


		this.dag = new DAG<>(new DAGElement<>(superTriangle, Triple.of(p0, p1, p2)));
		this.map.put(superTriangle, dag);
		this.finalized = false;
	}


	@Override
	public void compute() {
		// 1. insert points
		for(P p : points) {
			insert(p);
		}

		// 2. remove super triangle
		finalize();
	}

	@Override
	public E insert(P point) {
		Collection<DAG<DAGElement<P, F>>> leafs = locatePoint(point, true);
		if(leafs.size() == 0) {
			log.warn(point);
		}
//		assert leafs.size() == 2 || leafs.size() == 1 || leafs.size() > 0;

		// point is inside a triangle
		if(leafs.size() == 1) {
			log.info("splitTriangle:" + point);
			F face = leafs.stream().findAny().get().getElement().getFace();
			splitTriangle(face, point,  true);
			return mesh.getEdge(point);
		} // point lies on an edge of 2 triangles
		else if(leafs.size() == 2) {
			Iterator<DAG<DAGElement<P, F>>> it = leafs.iterator();
			log.info("splitEdge:" + point);
			E halfEdge = findTwins(it.next().getElement().getFace(),  it.next().getElement().getFace()).get();
			splitEdge(point, halfEdge, true);
			return mesh.getEdge(point);
		}
		else if(leafs.size() == 0) {
			// problem due numerical calculation.
			log.warn("no triangle was found! Maybe " + point + " lies outside of the triangulation." );
			return null;
		}
		else {
			log.warn("ignore insertion point, since this point already exists!");
			F face = leafs.iterator().next().getElement().getFace();
			Optional<E> optHe = mesh.streamEdges(face).filter(he -> mesh.getVertex(he).equals(point)).findAny();

			if(optHe.isPresent()) {
				return optHe.get();
			}
			else {
				return null;
			}
		}
	}

	/**
	 * Removes the super triangle from the mesh data structure.
	 */
	public void finalize() {
		if(!finalized) {
			// we have to use other halfedges than he1 and he2 since they might be deleted
			// if we deleteBoundaryFace he0!
			List<F> faces1 = IteratorUtils.toList(new AdjacentFaceIterator(mesh, he0));
			List<F> faces2 = IteratorUtils.toList(new AdjacentFaceIterator(mesh, he1));
			List<F> faces3 = IteratorUtils.toList(new AdjacentFaceIterator(mesh, he2));

			faces1.removeIf(f -> mesh.isBoundary(f));
			faces1.forEach(f -> deleteBoundaryFace(f));

			faces2.removeIf(f -> mesh.isDestroyed(f) || mesh.isBoundary(f));
			faces2.forEach(f -> deleteBoundaryFace(f));

			faces3.removeIf(f -> mesh.isDestroyed(f) || mesh.isBoundary(f));
			faces3.forEach(f -> deleteBoundaryFace(f));

			finalized = true;
		}
	}

	public boolean isDeletionOk(final F face) {
		if(mesh.isDestroyed(face)) {
			return false;
		}

		for(E halfEdge : mesh.getEdgeIt(face)) {
			if(mesh.isBoundary(mesh.getTwin(halfEdge))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Deletes a face assuming that the face contains at least one boundary edge, otherwise the
	 * deletion will not result in an feasibly triangulation.
	 *
	 * @param face the face that will be deleted, which as to be adjacent to the boundary.
	 */
	public void deleteBoundaryFace(final F face) {
		assert isDeletionOk(face);

		// 3 cases: 1. triangle consist of 1, 2 or 3 boundary edges
		List<E> boundaryEdges = new ArrayList<>(3);
		List<E> nonBoundaryEdges = new ArrayList<>(3);

		for(E halfEdge : mesh.getEdgeIt(face)) {
			if(mesh.isBoundary(mesh.getTwin(halfEdge))) {
				boundaryEdges.add(halfEdge);
			}
			else {
				nonBoundaryEdges.add(halfEdge);
			}
		}

		if(boundaryEdges.size() == 3) {
			// release memory
			mesh.getEdges(face).forEach(halfEdge -> mesh.destroyEdge(halfEdge));
		}
		else if(boundaryEdges.size() == 2) {
			E toB = mesh.isBoundary(mesh.getTwin(mesh.getNext(boundaryEdges.get(0)))) ? boundaryEdges.get(0) : boundaryEdges.get(1);
			E toF = mesh.isBoundary(mesh.getTwin(mesh.getNext(boundaryEdges.get(0)))) ? boundaryEdges.get(1) : boundaryEdges.get(0);
			E nB = nonBoundaryEdges.get(0);
			mesh.setFace(nB, mesh.getTwinFace(toF));
			mesh.setNext(nB, mesh.getNext(mesh.getTwin(toB)));
			mesh.setPrev(nB, mesh.getPrev(mesh.getTwin(toF)));
			mesh.setEdge(mesh.getFace(mesh.getTwin(toF)), nB);

			//this.face = mesh.getTwinFace(toF);

			// release memory
			mesh.destroyEdge(toF);
			mesh.destroyEdge(toB);

		}
		else {
			E boundaryHe = boundaryEdges.get(0);
			E prec = mesh.getPrev(mesh.getTwin(boundaryHe));
			E succ = mesh.getNext(mesh.getTwin(boundaryHe));

			E next = mesh.getNext(boundaryHe);
			E prev = mesh.getPrev(boundaryHe);
			mesh.setPrev(next, prec);
			mesh.setFace(next, mesh.getTwinFace(boundaryHe));

			mesh.setNext(prev, succ);
			mesh.setFace(prev, mesh.getTwinFace(boundaryHe));

			mesh.setEdge(mesh.getFace(prec), prec);

			//this.face = mesh.getFace(prec);
			// release memory
			mesh.destroyEdge(boundaryHe);
		}

		mesh.destroyFace(face);
		map.remove(face);
	}

	@Override
	public IMesh<P, E, F> getMesh() {
		return mesh;
	}

	@Override
	public Optional<F> locate(final IPoint point) {
		Optional<DAG<DAGElement<P, F>>> optDag = locatePoint(point, false).stream().findAny();
		if(optDag.isPresent()) {
			return Optional.of(optDag.get().getElement().getFace());
		}
		else {
			return Optional.empty();
		}
	}

	@Override
	public Set<F> getFaces() {
		return streamFaces().collect(Collectors.toSet());
	}

	@Override
	public Stream<F> streamFaces() {
		return stream();
	}

	@Override
	public void remove(P point) {
		throw new UnsupportedOperationException("not jet implemented.");
	}

	public Collection<VTriangle> getTriangles() {
		return stream().map(face -> faceToTriangle(face)).collect(Collectors.toSet());
	}

	public Set<VLine> getEdges() {
		return getTriangles().stream().flatMap(triangle -> triangle.getLineStream()).collect(Collectors.toSet());
	}

	private VTriangle faceToTriangle(final F face) {
		List<P> points = mesh.getEdges(face).stream().map(edge -> mesh.getVertex(edge)).collect(Collectors.toList());
		P p1 = points.get(0);
		P p2 = points.get(1);
		P p3 = points.get(2);
		return new VTriangle(new VPoint(p1.getX(), p1.getY()), new VPoint(p2.getX(), p2.getY()), new VPoint(p3.getX(), p3.getY()));
	}

	private Collection<DAG<DAGElement<P, F>>> locatePoint(final IPoint point, final boolean insertion) {

		Set<DAG<DAGElement<P, F>>> leafs = new HashSet<>();
		LinkedList<DAG<DAGElement<P, F>>> nodesToVisit = new LinkedList<>();
		nodesToVisit.add(dag);

		while(!nodesToVisit.isEmpty()) {
			DAG<DAGElement<P, F>> currentNode = nodesToVisit.removeLast();
			if(currentNode.getElement().getTriangle().isPartOf(point, eps)) {
				if(currentNode.isLeaf() && !mesh.isDestroyed(currentNode.getElement().getFace())) {
					leafs.add(currentNode);

					// if we are not interested in insertion we just want to find one triangle.
					if(!insertion) {
						return leafs;
					}
				}
				else {
					nodesToVisit.addAll(currentNode.getChildren());
				}
			}
		}

		return leafs;
	}

	public DAG<DAGElement<P, F>> getDag() {
		return dag;
	}

	/**
	 * Checks if the edge xy of the triangle xyz is illegal with respect to a point p, which is the case if:
	 * There is a point p and a triangle yxp and p is in the circumscribed cycle of xyz. The assumption is
	 * that the triangle yxp exists.
	 *
	 * @param edge  the edge that might be illegal
	 * @return true if the edge with respect to p is illegal, otherwise false
	 */
	@Override
	public boolean isIllegal(E edge) {
		return isIllegal(edge, mesh);
	}

	public static <P extends IPoint, E extends IHalfEdge<P>, F extends IFace<P>> boolean isIllegal(E edge, IMesh<P, E, F> mesh) {
		if(!mesh.isBoundary(mesh.getTwinFace(edge))) {
			P p = mesh.getVertex(mesh.getNext(edge));
			E t0 = mesh.getTwin(edge);
			E t1 = mesh.getNext(t0);
			E t2 = mesh.getNext(t1);

			P x = mesh.getVertex(t0);
			P y = mesh.getVertex(t1);
			P z = mesh.getVertex(t2);
			VTriangle triangle = new VTriangle(new VPoint(x), new VPoint(y), new VPoint(z));
			return triangle.isInCircumscribedCycle(p);
		}
		return false;
	}

	/*public static <P extends IPoint> boolean isIllegalEdge(final E edge){
		P p = edge.getNext().getEnd();

		if(!edge.isBoundary() && !edge.getTwin().isBoundary()) {
			P x = edge.getTwin().getEnd();
			P y = edge.getTwin().getNext().getEnd();
			P z = edge.getTwin().getNext().getNext().getEnd();
			VTriangle triangle = new VTriangle(new VPoint(x.getX(), x.getY()), new VPoint(y.getX(), y.getY()), new VPoint(z.getX(), z.getY()));
			return triangle.isInCircumscribedCycle(p);
		}
		return false;
	}*/

	@Override
	public void flipEdgeEvent(F f1, F f2) {
		DAG<DAGElement<P, F>> f1Dag = map.remove(f1);
		DAG<DAGElement<P, F>> f2Dag = map.remove(f2);
		List<P> points1 = mesh.getVertices(f1);
		List<P> points2 = mesh.getVertices(f2);

		DAG<DAGElement<P, F>> newf1Dag = new DAG<>(new DAGElement(f1, Triple.of(points1.get(0), points1.get(1), points1.get(2))));
		DAG<DAGElement<P, F>> newf2Dag = new DAG<>(new DAGElement(f2, Triple.of(points2.get(0), points2.get(1), points2.get(2))));

		f1Dag.addChild(newf1Dag);
		f1Dag.addChild(newf2Dag);

		f2Dag.addChild(newf1Dag);
		f2Dag.addChild(newf2Dag);

		map.put(f1, newf1Dag);
		map.put(f2, newf2Dag);
	}

	@Override
	public void splitFaceEvent(final F original, final F[] faces) {
		DAG<DAGElement<P, F>> faceDag = map.remove(original);
		for(F face : faces) {
			List<P> points = mesh.getVertices(face);
			DAG<DAGElement<P, F>> newFaceDag = new DAG<>(new DAGElement(face, Triple.of(points.get(0), points.get(1), points.get(2))));
			faceDag.addChild(newFaceDag);
			map.put(face, newFaceDag);
		}
	}


	@Override
	public Iterator<F> iterator() {
		return new FaceIterator(mesh);
	}

	public Stream<F> stream() {
		return StreamSupport.stream(this.spliterator(), false);
	}


	// TODO: the following code can be deleted, this is only for visual checks
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		int height = 700;
		int width = 700;
		int max = Math.max(height, width);

		Set<VPoint> points = new HashSet<>();
		/*points.add(new VPoint(20,20));
		points.add(new VPoint(20,40));
		points.add(new VPoint(75,53));
		points.add(new VPoint(80,70));*/

		Random r = new Random();
		for(int i=0; i< 1000; i++) {
			VPoint point = new VPoint(width*r.nextDouble(), height*r.nextDouble());
			points.add(point);
		}

		IPointConstructor<VPoint> pointConstructor =  (x, y) -> new VPoint(x, y);
		long ms = System.currentTimeMillis();
		IncrementalTriangulation<VPoint, PHalfEdge<VPoint>, PFace<VPoint>> bw = new IncrementalTriangulation<>(new PMesh<>(pointConstructor), points, pointConstructor);
		bw.compute();
		Set<VLine> edges = bw.getEdges();
		edges.addAll(bw.getTriangles().stream().map(triangle -> new VLine(triangle.getIncenter(), triangle.p1)).collect(Collectors.toList()));
		System.out.println(System.currentTimeMillis() - ms);

		JFrame window = new JFrame();
		window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		window.setBounds(0, 0, max, max);
		window.getContentPane().add(new Lines(edges, points, max));
		window.setVisible(true);

		ms = System.currentTimeMillis();
		BowyerWatsonSlow<VPoint> bw2 = new BowyerWatsonSlow<VPoint>(points, (x, y) -> new VPoint(x, y));
		bw2.execute();
		Set<VLine> edges2 = bw2.getTriangles().stream()
				.flatMap(triangle -> triangle.getLineStream()).collect(Collectors.toSet());
		System.out.println(System.currentTimeMillis() - ms);

		JFrame window2 = new JFrame();
		window2.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		window2.setBounds(0, 0, max, max);
		window2.getContentPane().add(new Lines(edges2, points, max));
		window2.setVisible(true);

		UniformTriangulation<VPoint, PHalfEdge<VPoint>, PFace<VPoint>> uniformTriangulation = new UniformTriangulation<>(new PMesh<>(pointConstructor),0, 0, width, height, 10.0, (x, y) -> new VPoint(x, y));
		uniformTriangulation.compute();
		uniformTriangulation.finalize();
		Set<VLine> edges3 = uniformTriangulation.getEdges();

		JFrame window3 = new JFrame();
		window3.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		window3.setBounds(0, 0, max, max);
		window3.getContentPane().add(new Lines(edges3, edges3.stream().flatMap(edge -> edge.streamPoints()).collect(Collectors.toSet()), max));
		window3.setVisible(true);

		UniformRefinementTriangulation<VPoint, PHalfEdge<VPoint>, PFace<VPoint>> uniformRefinement = new UniformRefinementTriangulation<>(new PMesh<>(pointConstructor), 0, 0, width, height, (x, y) -> new VPoint(x, y), Arrays.asList(new VRectangle(200, 200, 100, 200)), p -> 10.0);
		uniformRefinement.compute();
		Set<VLine> edges4 = uniformRefinement.getEdges();

		JFrame window4 = new JFrame();
		window4.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		window4.setBounds(0, 0, max, max);
		window4.getContentPane().add(new Lines(edges4, edges4.stream().flatMap(edge -> edge.streamPoints()).collect(Collectors.toSet()), max));
		window4.setVisible(true);
	}

	private static class Lines extends JComponent{
		private Set<VLine> edges;
		private Set<VPoint> points;
		private final int max;

		public Lines(final Set<VLine> edges, final Set<VPoint> points, final int max){
			this.edges = edges;
			this.points = points;
			this.max = max;
		}

		public void paint(Graphics g) {
			Graphics2D g2 = (Graphics2D) g;
			g2.setBackground(Color.white);
			g2.setStroke(new BasicStroke(1.0f));
			g2.setColor(Color.black);
			g2.draw(new VRectangle(200, 200, 100, 200));
			g2.setColor(Color.gray);
			//g2.translate(200, 200);
			//g2.scale(0.2, 0.2);

			g2.draw(new VRectangle(200, 200, 100, 200));

			edges.stream().forEach(edge -> {
				Shape k = new VLine(edge.getP1().getX(), edge.getP1().getY(), edge.getP2().getX(), edge.getP2().getY());
				g2.draw(k);
			});

			points.stream().forEach(point -> {
				VCircle k = new VCircle(point.getX(), point.getY(), 1.0);
				g2.draw(k);
			});

		}
	}
}