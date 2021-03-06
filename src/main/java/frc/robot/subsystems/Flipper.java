// RobotBuilder Version: 2.0
//
// This file was generated by RobotBuilder. It contains sections of
// code that are automatically generated and assigned by robotbuilder.
// These sections will be updated in the future when you export to
// Java from RobotBuilder. Do not put any code or make any change in
// the blocks indicating autogenerated code or it will be lost on an
// update. Deleting the comments indicating the section will prevent
// it from being updated in the future.


package frc.robot.subsystems;

import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.DoubleSolenoid.Value;
import edu.wpi.first.wpilibj.command.Subsystem;
import frc.robot.RobotMap;
public class Flipper extends Subsystem {

    public DoubleSolenoid flipper;

    public Flipper() {
        flipper = new DoubleSolenoid(0, RobotMap.FLIPPER_F, RobotMap.FLIPPER_R);
        addChild("Flipper",flipper);

        setFlipper(false);
    }

    @Override
    public void initDefaultCommand() {
        // Set the default command for a subsystem here.
        // setDefaultCommand(new MySpecialCommand());
    }

    @Override
    public void periodic() {
        // Put code here to be run every loop

    }

    // Put methods for controlling this subsystem
    // here. Call these from Commands.

    //interaction
    public boolean getIsUp(){
        if(flipper.get()==Value.kForward){
            return true;
        }
        else if(flipper.get()==Value.kReverse){
            return false;
        }
        else return false;
    }

    public void setFlipper(Value state){
        flipper.set(state);
    }
    public void setFlipper(boolean extend){
        Value state = (extend)? Value.kReverse:Value.kForward;
        flipper.set(state);
    }

}

