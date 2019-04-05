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


import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.DemandType;
import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.StatusFrame;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import frc.robot.common.*;
import frc.robot.Robot;
import frc.robot.RobotMap;
import frc.robot.subsystems.armCommands.*;
import frc.robot.subsystems.liftgroupCommands.LiftSetCargo;
import frc.robot.subsystems.manipulatorCommands.*;
import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.command.Scheduler;
import edu.wpi.first.wpilibj.command.Subsystem;

public class Arm extends Subsystem {

    public WPI_TalonSRX wrist;

    public DigitalInput hatchButton = new DigitalInput(4);

    private final int startPos = 280;
    private int target = startPos;
    private int targetA = target;//adjusted target
    private double akP = 2.2;
    private double akI = 0;//0.01
    private double akD = 50;
    private double akF = 1023.0/210.0;
    private double akPeak = 1;
    private double akRamp = 0.08;
    private int akCruise = 140;
    private int akCruiseItem = 120;
    private int akAccel = 270;
    private int akAccelItem = 220;
    //behavior constants
    public final double akRestingForce = 0.07;//forward pressure while resting
    public final double akAntiArm = 0.08;//percent with unburdened arm(counter gravity)
    public final double akAntiItem = 0.13;//percent with burdened arm
    private double manualForce = 0;
    //state
    private int pos = startPos;

    private boolean manual=false;

    private boolean armHasItem = false;
    private boolean armHadItem = false;
    private boolean armGotItem = false;
    private boolean armLostItem = false;

    private double lastTime=0;

    private boolean button = false;
    private boolean lastButton = false;
    private boolean buttonPressed = false;
    private boolean buttonReleased = false;
    private boolean buttonDisable = true;

    private boolean liftResting = true;
    private boolean liftWasResting = false;
    private boolean liftBecameResting = true;
    private boolean liftBecameUnresting = false;

    private boolean intakeBackdriving = false;
    private boolean intakeWasBackdriving = false;
    private boolean intakeBecameBackdriving = false;
    private boolean intakeBecameUnbackdriving = false;

    public Arm() {
        wrist = new WPI_TalonSRX(7);

        Config.configAllStart(wrist);

        wrist.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Absolute, RobotMap.P_IDX, Config.kTimeout);
        wrist.setStatusFramePeriod(StatusFrame.Status_13_Base_PIDF0, 30, Config.kTimeout);
        Config.configSensor(wrist, startPos);
        wrist.configForwardSoftLimitEnable(true, Config.kTimeout);
        wrist.configReverseSoftLimitEnable(true, Config.kTimeout);
        wrist.configForwardSoftLimitThreshold(RobotMap.ARM_FAR_FORWARD+10, Config.kTimeout);
        wrist.configReverseSoftLimitThreshold(-10, Config.kTimeout);
        wrist.setInverted(false);
        wrist.setSensorPhase(false);
        Config.configCruise(akCruise, wrist);
        Config.configAccel(akAccel, wrist);
        wrist.configMotionSCurveStrength(6, Config.kTimeout);
        Config.configClosed(wrist, akP, akI, akD, akF, akPeak, akRamp);
        wrist.config_IntegralZone(RobotMap.P_IDX, RobotMap.ARM_ERROR, Config.kTimeout);
    }

    @Override
    public void initDefaultCommand() {
        setDefaultCommand(new ArmManual());
        // Set the default command for a subsystem here.
        // setDefaultCommand(new MySpecialCommand());
    }

    @Override
    public void periodic() {
        // Put code here to be run every loop
        checkState();

        if(armGotItem){
            Config.configCruise(akCruiseItem, wrist);
            Config.configAccel(akAccelItem, wrist);
        }
        else if(armLostItem){
            Config.configCruise(akCruise, wrist);
            Config.configAccel(akAccel, wrist);
        }

        //if auto cycling disable when trigger pressed
        if(!buttonDisable && buttonPressed && Timer.getFPGATimestamp()-lastTime>=1.5){//if manual control, react to button
            if(!armHasItem){
                Scheduler.getInstance().add(new OpenClaw());
            }
            else{
                Scheduler.getInstance().add(new PlaceHatch());
            }
        }

        if(intakeBecameBackdriving) new ArmSet(RobotMap.ARM_CLOSE_BACKDRIVE).start();
        else if(manualForce!=0 && intakeBecameUnbackdriving) new ArmSetSafe().start();;
        
        targetA=target;

        //intake compensate
        targetA=Math.max(((Robot.intake.getBackdriving() && !getHasItem())? RobotMap.ARM_CLOSE_BACKDRIVE:RobotMap.ARM_CLOSE_FORWARD), targetA);
        //avoid pegs
        targetA=Math.min(((Robot.elevator.getPos()<=RobotMap.ELEV_HATCH1+RobotMap.ELEV_ERROR)? RobotMap.ARM_HATCH_OUT:RobotMap.ARM_FAR_FORWARD), targetA);
        //avoid pid pressure
        targetA=Math.min(((Robot.elevator.getPos()<=RobotMap.ELEV_HATCH1-RobotMap.ELEV_ERROR)? startPos:RobotMap.ARM_FAR_FORWARD), targetA);
        //dont break the chain
        targetA=Convert.limit(RobotMap.ARM_FAR_BACKWARD, RobotMap.ARM_FAR_FORWARD, targetA);

        double ff = calcGrav();//feed forward

        

        //if(liftResting) setWrist(akRestingForce);
        if(liftBecameResting) manualForce=akRestingForce;
        else if(liftBecameUnresting) manualForce=0;
        if(!manual){
            if(manualForce==0) aMotionPID(targetA, ff);
            else setWrist(manualForce);
        }

        putNetwork();
    }

    // Put methods for controlling this subsystem
    // here. Call these from Commands.
    private void checkState(){//state changes
        pos=getPos();

        armHasItem=getHasItem();
        armGotItem=armHasItem&&!armHadItem;
        armLostItem=!armHasItem&&armHadItem;
        armHadItem=armHasItem;

        button=getButton();
        buttonPressed=button&&!lastButton;
        buttonReleased=!button&&lastButton;
        lastButton=button;      

        liftResting = Robot.elevator.getIsResting();
        liftBecameResting = liftResting && !liftWasResting;
        liftBecameUnresting = !liftResting && liftWasResting;
        liftWasResting=liftResting;

        intakeBackdriving = Robot.intake.getBackdriving();
        intakeBecameBackdriving = intakeBackdriving && !intakeWasBackdriving;
        intakeBecameUnbackdriving = !intakeBackdriving && intakeWasBackdriving;
        intakeWasBackdriving = intakeBackdriving;
    }
    private void putNetwork(){
        Network.put("Arm Button", button);
        Network.put("Arm Target", target);
        Network.put("Arm TargetA", targetA);
        Network.put("Arm Pos", pos);
        Network.put("Arm Deg", getDeg());
        Network.put("Arm Native", Convert.getNative(wrist));
        Network.put("Arm Power", manualForce);
    }
    
    private void aMotionPID(double pos){
		wrist.set(ControlMode.MotionMagic, pos);
	}
	private void aMotionPID(double pos, double feed){
		wrist.set(ControlMode.MotionMagic, pos, DemandType.ArbitraryFeedForward, feed);
	}

    private double calcGrav(){//resist gravity
        double gravity = -Math.sin(Math.toRadians(getDeg()));//0 degrees is straight up, so gravity is a sin curve
        double counterForce = (gravity*((getHasItem())? akAntiItem:akAntiArm));//multiply by the output percent for holding stable while 90 degrees
        counterForce = Convert.limit(counterForce);
        return counterForce;
    }
    public void setManualForce(double x){
        manualForce=x;
    }
    
    //interaction
    public boolean isTarget(){
        return (pos<=target+RobotMap.ARM_ERROR && pos>=target-RobotMap.ARM_ERROR);
    }
    public boolean isTarget(int t){
        return (pos<=t+RobotMap.ARM_ERROR && pos>=t-RobotMap.ARM_ERROR);
    }
    public boolean isTarget(int pos, int target){
        return (pos<=target+RobotMap.ARM_ERROR && pos>=target-RobotMap.ARM_ERROR);
    }
    
    public int getPos(){
        return wrist.getSelectedSensorPosition();
    }
    public double getDeg(){
        return Convert.getDegrees(getPos());
    }
    public int getTarget(){
        return target;
    }
    public boolean getHasItem(){
        return Robot.manipulator.getIsOpen();
    }
    public boolean getButton(){
        return !hatchButton.get();
    }
    public boolean getButtonPressed(){
        return buttonPressed;
    }
    public boolean getButtonReleased(){
        return buttonReleased;
    }
    public boolean getButtonDisabled(){
        return buttonDisable;
    }

    public void setTarget(int t){
        target=t;
    }
    public void setIsManual(boolean b){
        manual=b;
    }
    public void setLastButtonTime(){
        lastTime=Timer.getFPGATimestamp();
    }
    public void setButtonDisable(boolean disabled){
        buttonDisable=disabled;
    }
    public void setWrist(double x){
		wrist.set(ControlMode.PercentOutput, x, DemandType.ArbitraryFeedForward, calcGrav());
    }
}

