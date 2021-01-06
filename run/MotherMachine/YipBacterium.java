package Fall2020;

import bsim.BSim;
import bsim.BSimChemicalField;
import bsim.capsule.BSimCapsuleBacterium;
import javax.vecmath.Vector3d;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.List;
import java.lang.Math;
import java.util.Random;
/**
 */
public class Bacterium extends BSimCapsuleBacterium {

    //function you call when you want to make a new bacterium object
    public Bacterium(BSim sim, Vector3d px1, Vector3d px2){
        // "Bacterium" is a type of capsule bacterium, so we need to do all the things that a CapsuleBacterium does first.
        // This is the purpose of super(). The function super() initializes this bacterium object by first
        // referring to it as a capsulebacterium.
        super(sim, px1, px2);
    }

    // in case we want our bacteria to do anything special, we can add things to action() here that an ordinary
    // capsulebacterium wouldn't do.
    @Override
    public void action() { //runs at every time step
        super.action();
    }

    // allows us to change growthrate mechanics for individual cells
    @Override
    public void setK_growth(double k_growth) {
        super.setK_growth(k_growth);
    }

    // allows us to change the division threshold of individual cells
    public void setElongationThreshold(double len) { this.L_th=len; }

    // this function is called when the bacterium has passed its' division threshold and is ready to divide.
    public Bacterium divide() {
        Vector3d randomVec = new Vector3d(rng.nextDouble()/100,rng.nextDouble()/100,rng.nextDouble()/100);
        System.out.println("Bacterium " + this.id + " is dividing...");

        Vector3d u = new Vector3d(); u.sub(this.x2, this.x1);

        //decides where to split bacterium
        double divPert = 0.1*L*(rng.nextDouble() - 0.5); //0.1 is arbitrary scaling here.

        // get length of bacterium
        double L_actual = u.length();

        // computes lengths of child bacteria
        double L1 = L_actual*0.5*(1 + divPert) - radius;
        double L2 = L_actual*0.5*(1 - divPert) - radius;

        //finds new endpoints of mother and daughter
        Vector3d x2_new = new Vector3d();
        x2_new.scaleAdd(L1/L_actual, u, this.x1);
        Vector3d longVec = new Vector3d();
        longVec.scaleAdd(-1,this.x2,this.x1); //push along bacterium length
        longVec.scale(0.05*rng.nextDouble()); //push is applied to bacterium
        // impulse, not a force.
        longVec.add(randomVec);
        x2_new.add(longVec);

        Vector3d x1_child = new Vector3d();
        x1_child.scaleAdd(-(L2/L_actual), u, this.x2);
        Vector3d longVec2=new Vector3d();
        longVec2.scaleAdd(-1,longVec);
        x1_child.add(longVec2); //push applied to child cell


        // Set the child cell.
        //creates new bacterium called child and adds it to the lists, gives posns, infected status and chemical field status
        Bacterium child = new Bacterium(sim, x1_child, new Vector3d(this.x2));
        this.initialise(L1, this.x1, x2_new);
        child.L = L2;

        // prints a line whenever a new bacterium is made
        System.out.println("Child ID id " + child.id);
        return child;
    }

    // seemingly unused code
//    int count=0;
//    public void set_count(int n){
//        this.count = n;
//    }
//    public int get_count(){
//        return count;
//    }

}
