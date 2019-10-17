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
import com.ctre.phoenix.motorcontrol.InvertType;
import com.ctre.phoenix.motorcontrol.StatusFrame;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import frc.robot.common.*;
import frc.robot.Robot;
import frc.robot.RobotMap;
import frc.robot.subsystems.elevatorCommands.*;

import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.command.Subsystem;

public class Elevator extends Subsystem {
    public WPI_TalonSRX front;
    public WPI_TalonSRX back;
    public DigitalInput stageTop = new DigitalInput(RobotMap.STAGETOP);
    public DigitalInput carriageTop = new DigitalInput(RobotMap.CARRIAGETOP);
    public DigitalInput stageBot = new DigitalInput(RobotMap.STAGEBOT);
    public DigitalInput carriageBot = new DigitalInput(RobotMap.CARRIAGEBOT);

    //pid
    private int target=0;
    private int targetA=target;
    private double ekP = 0.7;
    private double ekI = 0.002;//0.005
    private double ekD = 35;
    private double ekF = 1023.0/4900.0;
    private double ekPeak = 1;
    private double ekRamp = 0.1;
    private int ekCruise = 4750;
    private int ekAccel = 12500;//encoder counts per 100 ms per second
    //behavior
    public final double ekAntiGrav = 0.08;

    private boolean isSupply = false;

    private boolean manual=false;

    public Elevator() {
        front = new WPI_TalonSRX(RobotMap.ELEV_F);
        
        back = new WPI_TalonSRX(RobotMap.ELEV_B);
        
        Config.configAllStart(front);
        Config.configAllStart(back);

        back.follow(front);
        front.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Absolute, RobotMap.P_IDX, Config.kTimeout);
        front.setStatusFramePeriod(StatusFrame.Status_13_Base_PIDF0, 30, Config.kTimeout);
        Config.configSensor(front);
        front.configForwardSoftLimitEnable(true, Config.kTimeout);
        front.configReverseSoftLimitEnable(true, Config.kTimeout);
        front.configForwardSoftLimitThreshold(RobotMap.ELEV_HATCH3+30, Config.kTimeout);
        front.configReverseSoftLimitThreshold(RobotMap.ELEV_BOTTOM-15, Config.kTimeout);
        front.setInverted(true);
        back.setInverted(InvertType.FollowMaster);
        front.setSensorPhase(true);
        Config.configCruise(ekCruise, front);
        Config.configAccel(ekAccel, front);
        front.configMotionSCurveStrength(8, Config.kTimeout);
        Config.configClosed(front, ekP, ekI, ekD, ekF, ekPeak, ekRamp);
        front.config_IntegralZone(RobotMap.P_IDX, RobotMap.ELEV_ERROR/2, Config.kTimeout);
    }

    @Override
    public void initDefaultCommand() {
        // Set the default command for a subsystem here.
        // setDefaultCommand(new MySpecialCommand());
        setDefaultCommand(new ElevatorManual());
    }

    @Override
    public void periodic() {
        // Put code here to be run every loop
        checkState();

        targetA=Convert.limit(RobotMap.ELEV_BOTTOM, RobotMap.ELEV_HATCH3, target);//physical limits

        if(Robot.arm.getPos()<RobotMap.ARM_CLOSE_FORWARD-2*RobotMap.ARM_ERROR) targetA=Math.min(getPos(), target);

        if(getWantRest()){
            targetA=RobotMap.ELEV_BOTTOM;
            if(!getIsResting()) targetA=RobotMap.ELEV_BOTTOM-RobotMap.ELEV_ERROR;
        }

        /*
        if(isTarget(RobotMap.ELEV_CARGO) && isTarget((int)target, RobotMap.ELEV_CARGO)){
            Robot.intake.setBackdriving(true);
        }
        else{
            Robot.intake.setBackdriving(false);
        }
        */

        if(getIsResting()) setElev(0);
        else if(!manual) eMotionPID(targetA, ekAntiGrav);

        putNetwork();
    }

    // Put methods for controlling this subsystem
    // here. Call these from Commands.

    private void checkState(){//state changes
    }

    private void putNetwork(){
        Network.put("Carriage Top", getCarrTop());
        Network.put("Carriage Bot", getCarrBot());
        Network.put("Stage Top", getStageTop());
        Network.put("Stage Bot", getStageBot());
        Network.put("Elev Pos", getPos());
        Network.put("Elev Target", targetA);
        Network.put("Elev Native", Convert.getNative(front));
    }

    private void eMotionPID(double pos){
		front.set(ControlMode.MotionMagic, pos);
	}
	private void eMotionPID(double pos, double feed){
		front.set(ControlMode.MotionMagic, pos, DemandType.ArbitraryFeedForward, feed);
	}
    
    public boolean isTarget(){//finish commands if the position meets target
        return (getPos()<=target+RobotMap.ELEV_ERROR && getPos()>=target-RobotMap.ELEV_ERROR);
    }
    public boolean isTarget(int target){
        return (getPos()<=target+RobotMap.ELEV_ERROR && getPos()>=target-RobotMap.ELEV_ERROR);
    }
    public boolean isTarget(int pos, int target){
        return (pos<=target+RobotMap.ELEV_ERROR && pos>=target-RobotMap.ELEV_ERROR);
    }

    //interaction
    public int getPos(){
        return front.getSelectedSensorPosition();
    }
    public boolean getIsResting(){
        return 
            ((getCarrBot()&&getStageBot())||
            isTarget(RobotMap.ELEV_BOTTOM-RobotMap.ELEV_ERROR+70))&&
            isTarget(target, RobotMap.ELEV_BOTTOM);
    }
    public boolean getWantRest(){
        int cutoff = RobotMap.ELEV_HATCH1-2*RobotMap.ELEV_ERROR;
        return (getPos()<=cutoff && target<=getPos()+RobotMap.ELEV_ERROR);
    }
    public boolean getCarrTop(){
        return !carriageTop.get();
    }
    public boolean getCarrBot(){
        return !carriageBot.get();
    }
    public boolean getStageTop(){
        return !stageTop.get();
    }
    public boolean getStageBot(){
        return !stageBot.get();
    }

    public boolean getIsSupply(){
        return isSupply;
    }

    public void setIsSupply(boolean is){
        isSupply=is;
    }

    public void setTarget(int t){
        target = t;
    }

    public void setIsManual(boolean b){
        manual=b;
    }
    public void setElev(double x){
		front.set(ControlMode.PercentOutput, x, DemandType.ArbitraryFeedForward, (getIsResting()? 0:ekAntiGrav));
    }
}

