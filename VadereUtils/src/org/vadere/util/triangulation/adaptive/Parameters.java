package org.vadere.util.triangulation.adaptive;

/**
 * Created by Matimati-ka on 27.09.2016.
 */
public class Parameters {
    final static double TOL = .1;
    final static double FSCALE = 1.2;
    final static double DELTAT = 0.2;
    public final static double h0 = 0.15;
    public final static boolean uniform = false;
    public final static String method = "Density"; // "Distmesh" or "Density"
    final static double qualityMeasurement = 0.875;
    final static double MINIMUM = 0.25;
    final static double DENSITYWEIGHT = 2;
    final static int NPOINTS = 100000;
    final static int SAMPLENUMBER = 10;
    final static int SAMPLEDIVISION = 10;
    static final int SEGMENTDIVISION = 0;
}
