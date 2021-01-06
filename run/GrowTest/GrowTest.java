package GrowTest;

import bsim.BSim;
import bsim.BSimChemicalField;
import bsim.BSimTicker;
import bsim.BSimUtils;
import bsim.capsule.BSimCapsuleBacterium;
import bsim.capsule.Mover;
import bsim.capsule.RelaxationMoverGrid;
import bsim.draw.BSimDrawer;
import bsim.draw.BSimP3DDrawer;
import bsim.export.BSimLogger;
import bsim.export.BSimPngExporter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import processing.core.PConstants;
import processing.core.PGraphics3D;

import javax.vecmath.Vector3d;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.List;

/**
 * Just a test to see if I can get bacteria to grow in a box and output their locations over time
 */
public class GrowTest {

    @Parameter(names = "-export", description = "Enable export mode.")
    private boolean export = true;

    @Parameter(names = "-dim", arity = 3, description = "The dimensions (x, y, z) of simulation environment (um).")
    public List<Double> simDimensions = new ArrayList<>(Arrays.asList(new Double[] {50., 50., 1.}));

    // Boundaries
    @Parameter(names = "-fixedbounds", description = "Enable fixed boundaries. (If not, one boundary will be leaky as real uf chamber).")
    private boolean fixedBounds = true;

    /**
     * Whether to enable growth in the ticker etc. or not...
     */
    private static final boolean WITH_GROWTH = true;


    public static void main(String[] args) {
        GrowTest bsim_ex = new GrowTest();

        new JCommander(bsim_ex, args);

        bsim_ex.run();
    }

    public void run() {
        /*********************************************************
         * Initialise parameters from command line
         */
        double simX = simDimensions.get(0);
        double simY = simDimensions.get(1);
        double simZ = simDimensions.get(2);
        
        int nStart = 2; // Initial number of cells

        long simulationStartTime = System.nanoTime();

        // create the simulation object
        BSim sim = new BSim();
        sim.setDt(0.01);				    // Simulation Timestep
        sim.setSimulationTime(50);       // 36000 = 10 hours; 600 minutes.
        sim.setTimeFormat("0.00");		    // Time Format for display
        sim.setBound(simX, simY, simZ);		// Simulation Boundaries
        sim.setSolid(true, true, true);

        /*********************************************************
         * Create the bacteria
         */

        // Track all of the bacteria in the simulation, for use of common methods etc
        final ArrayList<BSimCapsuleBacterium> bacteriaAll = new ArrayList();

        Random bacRng = new Random();

        generator:
        while(bacteriaAll.size() < nStart) {
            double bL = 1. + 0.1*(bacRng.nextDouble() - 0.5);
            double angle = bacRng.nextDouble()*2*Math.PI;

            Vector3d pos = new Vector3d(
                    1.1 + bacRng.nextDouble()*(sim.getBound().x - 2.2),
                    1.1 + bacRng.nextDouble()*(sim.getBound().y - 2.2),
                    0.5);
            // Test intersection

            Vector3d distance = new Vector3d(0,0,0);

            for(BSimCapsuleBacterium otherBac : bacteriaAll){
                distance.sub(otherBac.position, pos);
                if(distance.lengthSquared() < 4.5){
                    continue generator;
                }
            }

            GrowTestBacterium bac = new GrowTestBacterium(sim,
                    new Vector3d(pos.x - bL*Math.sin(angle), pos.y - bL*Math.cos(angle), pos.z),
                    new Vector3d(bL*Math.sin(angle) + pos.x, bL*Math.cos(angle) + pos.y, pos.z)
            );

            bac.L = bL;
            bacteriaAll.add(bac);
        }

        // Set up stuff for growth.
        final ArrayList<BSimCapsuleBacterium> act_born = new ArrayList();
        final ArrayList<BSimCapsuleBacterium> act_dead = new ArrayList();

        final Mover mover;

        mover = new RelaxationMoverGrid(bacteriaAll, sim);

        /*********************************************************
         * Set up the ticker
         */
        int LOG_INTERVAL = 1;

        BSimTicker ticker = new BSimTicker() {
            @Override
            public void tick() {
                // ********************************************** Action
                long startTimeAction = System.nanoTime();

                for(BSimCapsuleBacterium b : bacteriaAll) {
                    b.action();
                }

                long endTimeAction = System.nanoTime();
                if((sim.getTimestep() % LOG_INTERVAL) == 0) {
                    System.out.println("Action update for " + bacteriaAll.size() + " bacteria took " + (endTimeAction - startTimeAction)/1e6 + " ms.");
                }

                // ********************************************** Growth related activities if enabled.
                if(WITH_GROWTH) {

                    // ********************************************** Growth and division
                    startTimeAction = System.nanoTime();

                    for (BSimCapsuleBacterium b : bacteriaAll) {
                        b.grow();

                        // Divide if grown past threshold
                        if (b.L > b.L_th) {
                            act_born.add(b.divide());
                        }
                    }
                    bacteriaAll.addAll(act_born);
                    act_born.clear();

                    endTimeAction = System.nanoTime();
                    if ((sim.getTimestep() % LOG_INTERVAL) == 0) {
                        System.out.println("Growth and division took " + (endTimeAction - startTimeAction) / 1e6 + " ms.");
                    }

                    // ********************************************** Neighbour interactions
                    startTimeAction = System.nanoTime();

                    mover.move();

                    endTimeAction = System.nanoTime();
                    if ((sim.getTimestep() % LOG_INTERVAL) == 0) {
                        System.out.println("Wall and neighbour interactions took " + (endTimeAction - startTimeAction) / 1e6 + " ms.");
                    }

                    // ********************************************** Boundaries/removal
                    startTimeAction = System.nanoTime();
                    // Removal
                    for (BSimCapsuleBacterium b : bacteriaAll) {
//                         Kick out if past the top or bottom boundaries
//                        if ((b.x1.y < 0) && (b.x2.y < 0)) {
//                            act_dead.add(b);
//                        }
//                        if ((b.x1.y > sim.getBound().y) && (b.x2.y > sim.getBound().y)) {
//                            act_dead.add(b);
//                        }
                        // kick out if past any boundary
                        if(b.position.x < 0 || b.position.x > sim.getBound().x || b.position.y < 0 || b.position.y > sim.getBound().y || b.position.z < 0 || b.position.z > sim.getBound().z){
                            act_dead.add(b);
                        }
                    }
                    bacteriaAll.removeAll(act_dead);
                    act_dead.clear();

                    endTimeAction = System.nanoTime();
                    if ((sim.getTimestep() % LOG_INTERVAL) == 0) {
                        System.out.println("Death and removal took " + (endTimeAction - startTimeAction) / 1e6 + " ms.");
                    }
                }
            }
        };
        sim.setTicker(ticker);

        /*********************************************************
         * Set up the drawer
         */
        BSimDrawer drawer = new BSimP3DDrawer(sim, 800, 600) {
            /**
             * Draw the default cuboid boundary of the simulation as a partially transparent box
             * with a wireframe outline surrounding it.
             */
            @Override
            public void boundaries() {
                p3d.noFill();
                p3d.stroke(128, 128, 255);
                p3d.pushMatrix();
                p3d.translate((float)boundCentre.x,(float)boundCentre.y,(float)boundCentre.z);
                p3d.box((float)bound.x, (float)bound.y, (float)bound.z);
                p3d.popMatrix();
                p3d.noStroke();
            }

            @Override
            public void draw(Graphics2D g) {
                p3d.beginDraw();

                if(!cameraIsInitialised){
                    // camera(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ)
                    p3d.camera((float)bound.x*0.5f, (float)bound.y*0.5f,
                            // Set the Z offset to the largest of X/Y dimensions for a reasonable zoom-out distance:
                            simX > simY ? (float)simX : (float)simY,
//                            10,
                            (float)bound.x*0.5f, (float)bound.y*0.5f, 0,
                            0,1,0);
                    cameraIsInitialised = true;
                }

                p3d.textFont(font);
                p3d.textMode(PConstants.SCREEN);

                p3d.sphereDetail(10);
                p3d.noStroke();
                p3d.background(255, 255,255);

                scene(p3d);
                boundaries();
                time();

                p3d.endDraw();
                g.drawImage(p3d.image, 0,0, null);
            }

            /**
             * Draw the formatted simulation time to screen.
             */
            @Override
            public void time() {
                p3d.fill(0);
                //p3d.text(sim.getFormattedTimeHours(), 50, 50);
                p3d.text(sim.getFormattedTime(), 50, 50);
            }

            @Override
            public void scene(PGraphics3D p3d) {
                p3d.ambientLight(128, 128, 128);
                p3d.directionalLight(128, 128, 128, 1, 1, -1);

                for(BSimCapsuleBacterium b : bacteriaAll) {
                    draw(b, Color.blue);
                }
            }
        };
        sim.setDrawer(drawer);


        if(export) {
            String simParameters = "" + BSimUtils.timeStamp() + "__dim_" + simX + "_" + simY + "_" + simZ;

            if(fixedBounds){
                simParameters += "__fixedBounds";
            } else {
                simParameters += "__leakyBounds";
            }

            String filePath = BSimUtils.generateDirectoryPath("./results/" + simParameters + "/");
//            String filePath = BSimUtils.generateDirectoryPath("/home/am6465/tmp-results/" + simParameters + "/");


            /*********************************************************
             * Various properties of the simulation, for future reference.
             */
            BSimLogger metaLogger = new BSimLogger(sim, filePath + "simInfo.txt") {
                @Override
                public void before() {
                    super.before();
                    write("Simulation metadata.");
                    write("GrowTest based on ChenOscillator");
                    write("Simulation dimensions: (" + simX + ", " + simY + ", " + simZ + ")");

                    if(fixedBounds){
                        write("Boundaries: fixed");
                    } else {
                        write("Boundaries: leaky");
                    }
                }

                @Override
                public void during() {

                }
            };
            metaLogger.setDt(1);			// Set export time step
            sim.addExporter(metaLogger);

            /*********************************************************
             * Position is fixed at the start; we log it once.
             */
//            BSimLogger posLogger = new BSimLogger(sim, filePath + "position.csv") {
//                DecimalFormat formatter = new DecimalFormat("###.##", DecimalFormatSymbols.getInstance( Locale.ENGLISH ));
//
//                @Override
//                public void before() {
//                    super.before();
//                    write("Initial cell positions. Fixed for the duration.");
//                    write("per Act; per Rep; id, p1x, p1y, p1z, p2x, p2y, p2z");
//                    String buffer = new String();
//
//                    write("Activators");
//
//                    buffer = "";
//                    for(BSimCapsuleBacterium b : bacteriaAll) {
//                        buffer += b.id + "," + formatter.format(b.x1.x)
//                                + "," + formatter.format(b.x1.y)
//                                + "," + formatter.format(b.x1.z)
//                                + "," + formatter.format(b.x2.x)
//                                + "," + formatter.format(b.x2.y)
//                                + "," + formatter.format(b.x2.z)
//                                + "\n";
//                    }
//
//                    write(buffer);
//
//                    write("Repressors");
//
//                    buffer = "";
//                    for(BSimCapsuleBacterium b : bacteriaRepressors) {
//                        buffer += b.id + "," + formatter.format(b.x1.x)
//                                + "," + formatter.format(b.x1.y)
//                                + "," + formatter.format(b.x1.z)
//                                + "," + formatter.format(b.x2.x)
//                                + "," + formatter.format(b.x2.y)
//                                + "," + formatter.format(b.x2.z)
//                                + "\n";
//                    }
//
//                    write(buffer);
//                }
//
//                @Override
//                public void during() {
//
//                }
//            };
//            posLogger.setDt(30);			// Set export time step
//            sim.addExporter(posLogger);

            BSimLogger posLogger = new BSimLogger(sim, filePath + "position.csv") {
                DecimalFormat formatter = new DecimalFormat("###.##", DecimalFormatSymbols.getInstance( Locale.ENGLISH ));

                @Override
                public void before() {
                    super.before();
                    write("per Act; per Rep; id, p1x, p1y, p1z, p2x, p2y, p2z, px");
                }

                @Override
                public void during() {
                    String buffer = new String();

                    buffer += sim.getFormattedTime() + "\n";
                    write(buffer);

                    write("acts");

                    buffer = "";
                    for(BSimCapsuleBacterium b : bacteriaAll) {
                        buffer += b.id + "," + formatter.format(b.x1.x)
                                + "," + formatter.format(b.x1.y)
                                + "," + formatter.format(b.x1.z)
                                + "," + formatter.format(b.x2.x)
                                + "," + formatter.format(b.x2.y)
                                + "," + formatter.format(b.x2.z)
                                + "," + formatter.format(b.position.x)
                                + "\n";
                    }
                    write(buffer);
                }
            };
            posLogger.setDt(1);			// Set export time step
            sim.addExporter(posLogger);

            /**
             * Export a rendered image file
             */
            BSimPngExporter imageExporter = new BSimPngExporter(sim, drawer, filePath);
            imageExporter.setDt(30);
            sim.addExporter(imageExporter);

            sim.export();

            /**
             * Drawing a java plot once we're done?
             * See TwoCellsSplitGRNTest
             */

        } else {
            sim.preview();
        }

        long simulationEndTime = System.nanoTime();

        System.out.println("Total simulation time: " + (simulationEndTime - simulationStartTime)/1e9 + " sec.");
    }
}
