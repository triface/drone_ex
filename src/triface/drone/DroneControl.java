package triface.drone;

import java.util.Observable;
import java.util.Observer;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * Defines a drone with n engines and has methods for movement (roll, pitch and thrust)
 * The input takes n number of engines The roll is aligned (to y-axis) for the left and right movements. 
 * The pitch is aligned (to x-axis) for the forward and backward movements. 
 * The thrust is aligned (to z-axis) for the up and down movements. 
 * Additional movements for TakeOff, Landing and Hovering.
 * Drone has these statuses: Ready, Off, Hovering, Moving.
 * Drone has to be Hovering before any roll, pitch and thrust actions.
 * 
 * Has battery discharge timer that lowers 1 percent for every 5 seconds
 *  
 * @author sudhir
 *
 */

public class DroneControl extends Thread
{
	public enum DRONE_STATUS {READY, OFF, HOVERING, MOVING};

	private static final int MAX_ALTITUDE = 360;	//in inches
	private static final int TAKEOFF_ALTI = 120;	//in inches
	private static final int MOVING_ALTI_STEP = 1;	//in inches
	private static final int VERT_ALTI_STEP = 3;	//in inches
	
	private Engine[] engines;

	private final int n;
	private int stablePower;
	private int altitude;
	private double batteryCharge;
	private DRONE_STATUS status;
	private Timer batteryDischargeTimer;

	private ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

	public DroneControl(int n)
	{
		this.n = n;
		initComponents();
	}
	
	private void initComponents()
	{
		createEngines();
		System.out.println("Drone created with engines: " + n);
		batteryCharge = Math.round(Math.random() * 100);
		batteryDischargeTimer = new Timer();
		batteryDischargeTimer.scheduleAtFixedRate(dischargeTask, 5000, 5000);
	}
	
	public void shutdown()
	{
		
		try {
			System.out.println(toString());
			land();
			for (int i=0; i < n; i++)
				engines[i].shutdown();
	
			if (batteryDischargeTimer != null)
				batteryDischargeTimer.cancel();
			System.out.println("All engines shutdown");
			status = DRONE_STATUS.OFF;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			System.exit(0);
		}
	}
	
	private void createEngines()
	{
		//Register for distress signal
		Observer droneObs = new Observer() {
			@Override
			public void update(Observable arg0, Object arg1) {
				if (status != DRONE_STATUS.OFF)
					shutdown();
			}
		};
		
		engines = new Engine[n];
		boolean isClockwise = true;

		for (int i=0; i < n; i++)
		{
			engines[i] = new Engine(i, isClockwise, droneObs);
			engines[i].changePower(Engine.HOVERING_POWER);
			
			if (i%2 == 0)
				isClockwise = !isClockwise;
		}
		
		status = DRONE_STATUS.READY;
	}
	
	public double getBatteryCharge()
	{
        Lock readLock = rwLock.readLock();
        readLock.lock();
        try {
    		return batteryCharge;
        }
        finally 
        {
            readLock.unlock();
        }
	}

	public int getAltitude()
	{
        Lock readLock = rwLock.readLock();
        readLock.lock();
        try {
    		return altitude;
        }
        finally 
        {
            readLock.unlock();
        }
	}

	public void changeAltitude(int inches)
	{
		Lock writeLock = rwLock.writeLock();
		writeLock.lock();
        try {
        	int newAlt = altitude + inches;
        	if (newAlt > MAX_ALTITUDE)
        		altitude = MAX_ALTITUDE;
        	else if (newAlt < 0)
        		altitude = 0;
        	else
        		altitude = newAlt;
        }
        finally 
        {
        	writeLock.unlock();
        }
	}

	public DRONE_STATUS getStatus()
	{
		return status;
	}
	
	/**
	 * Lowers all engine power to zero and altitude to zero
	 * @throws DistressException
	 */
	public void land()
	throws DistressException
	{
		if (status == DRONE_STATUS.MOVING)
			stabilize();

		if (status != DRONE_STATUS.HOVERING)
		{
			System.err.println("Drone is not flying. No action");
			return;
		}
		
		System.out.println("Landing all engines");
		for (int i=0; i < n; i++)
		{
			engines[i].changePower(-1 * engines[i].getPower(), Engine.NUM_STEPS);
		}
		stablePower = 0;
		changeAltitude(-1 * getAltitude());
		status = DRONE_STATUS.READY;
	}

	/**
	 * Takes the drone to TAKEOFF_HEIGHT by setting all engine powers to TAKEOFF_POWER
	 * @throws DistressException
	 */
	public void takeoff()
	throws DistressException
	{
		if (status == DRONE_STATUS.HOVERING)
		{
			System.err.println("Drone is already flying. No action");
			return;
		}
		else if (status != DRONE_STATUS.READY)
			throw new DistressException("Drone not ready to takeoff");
		
		System.out.println("Lifting all engines");
		for (int i=0; i < n; i++)
		{
			engines[i].changePower(Engine.TAKEOFF_POWER - Engine.HOVERING_POWER, Engine.NUM_STEPS);
		}
		stablePower = engines[0].getPower();
		changeAltitude(TAKEOFF_ALTI);
		status = DRONE_STATUS.HOVERING;
	}

	/**
	 * If the drone is MOVING due to roll or pitch
	 * stabilize() will set all engine powers to one
	 * stable power that has been captured earlier.
	 */
	public void stabilize()
	{
		if (status == DRONE_STATUS.READY)
		{
			System.err.println("Drone is not flying. No action");
			return;
		}
		
		System.out.println("Stabilizing all engines");
		if (stablePower < Engine.HOVERING_POWER)
			stablePower = Engine.HOVERING_POWER;
		for (int i=0; i < n; i++)
		{
			engines[i].changePower(stablePower - engines[i].getPower());
		}
		status = DRONE_STATUS.HOVERING;
	}

	/**
	 * Change all engine powers by one step and 
	 * change altitude by one step 
	 * @param thrust (negative value to go Down)
	 * @throws DistressException
	 */
	public void moveUD(int thrust)
	throws DistressException
	{
		if (status == DRONE_STATUS.MOVING)
			stabilize();
		if (status != DRONE_STATUS.HOVERING)
		{
			System.err.println("Drone is not flying. No action");
			return;
		}
		
		for (int i=0; i < n; i++)
		{
			engines[i].changePower(thrust);
		}
		stablePower = engines[0].getPower();
	}
	
	//Inputs are delta powers
	public void move(int roll, int pitch, int thrust)
	throws DistressException
	{
		if (roll != 0)
			moveRL(roll);
		else if (pitch != 0)
			moveFB(pitch);
		else if (thrust != 0)
			moveUD(thrust);
		
	}
	
	/**
	 * Lower the power of left engines by a step to roll Left
	 * and vice versa 
	 * @param roll (negative value to go Left)
	 * @throws DistressException
	 */
	public void moveRL(int roll)
	throws DistressException
	{
		if (status == DRONE_STATUS.MOVING)
			stabilize();
		if (status != DRONE_STATUS.HOVERING)
		{
			System.err.println("Drone is not flying. No action");
			return;
		}
		
		status = DRONE_STATUS.MOVING;
		for (int i=0; i < n; i++)
		{
			if (roll < 0)
			{
				if (engines[i].isLeft())
				{
					System.out.println("Adjusting Left engine:" + engines[i].getEngineId());
					engines[i].changePower(roll);
					stablePower = engines[1].getPower();
				}
			}
			else if (!engines[i].isLeft())
			{
				System.out.println("Adjusting Right engine:" + engines[i].getEngineId());
				engines[i].changePower(roll);
				stablePower = engines[0].getPower();
			}
		}
	}
	
	/**
	 * Lower the power of front engines by a step to go Forward
	 * and vice versa 
	 * @param pitch (negative value to go Backward)
	 * @throws DistressException
	 */
	public void moveFB(int pitch)
	throws DistressException
	{
		if (status == DRONE_STATUS.MOVING)
			stabilize();
		if (status != DRONE_STATUS.HOVERING)
		{
			System.err.println("Drone is not flying. No action");
			return;
		}
		
		status = DRONE_STATUS.MOVING;
		if (pitch > 0)
		{
			System.out.println("Adjusting Front engines");
			engines[0].changePower(pitch);
			stablePower = engines[1].getPower();
			if (n > 2)
				engines[1].changePower(pitch);
		}
		else
		{
			System.out.print("Adjusting Back engines");
			engines[n-1].changePower(pitch);
			stablePower = engines[0].getPower();
			if (n > 2)
				engines[n-2].changePower(pitch);
		}
	}


	@Override
	public void run() {
		while (true)
		{
			try 
			{
				/* If the drone is drifting due to roll or pitch,
				 * it is gradually coming down as there is no change in upward thrust.
				 * So, the altitude will be dropping gradually until there is 
				 * manual intervention or auto stabilization or crash
				 */
				if (status == DRONE_STATUS.MOVING)
				{
					if (getAltitude() <= 24)
					{
						System.err.println("Drone altitude less than 2 ft and about to crash. Auto stabilizing");
						stabilize();
						break;
					}
					else if (getAltitude() <= 60)
						System.err.println("Drone altitude less than 5 ft. and moving down. Press H to stabilize");
						
					changeAltitude(-MOVING_ALTI_STEP);
					Thread.sleep(1000);
				}
				else
					Thread.sleep(1000);
			}
			catch (InterruptedException ie) 
			{
				ie.printStackTrace();
			}
		}
		
	}

	/**
	 * Task to monitor the drone battery. Lands and shuts down when battery < 5%.
	 * Decrements at the rate of 1% every 5 seconds
	 */
	private final TimerTask dischargeTask = new TimerTask()
	{
		final static int BATTERY_DECR = 1;
		boolean notified = false;
		@Override
		public void run()
		{
			Lock writeLock = rwLock.writeLock();
			writeLock.lock();
			try {
				double newBatChg = batteryCharge - BATTERY_DECR;
				if (newBatChg <= 5d)	//battery less than 5%
				{
					System.err.println("Drone has low battery: " + newBatChg
										+ ", sending distress signal");
					shutdown();
				}
				else if (newBatChg <= 20d && !notified)
				{
					System.err.println("ALERT: Drone battery less than 20%");
					notified = true;
				}
				batteryCharge = newBatChg;
			}
			finally {
				writeLock.unlock();
			}
		}
	};


	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder("Drone status: ");
		sb.append(getStatus());
		sb.append("\tBattery: ");
		sb.append(getBatteryCharge());
		sb.append("\tAltitude: ");
		sb.append(getAltitude());
		sb.append(" inches\nEngine Status:");
		for (int i=0; i < n; i++)
		{
			if (i%2 != 0)
				sb.append("\t");
			else
				sb.append("\n\t");
			sb.append(engines[i].toString());
		}
		return sb.toString();
	}

	public void processMove(String moveAct, Integer powerStep)
	throws DistressException
	{
		//Defaulting to one step change, 
		//multiple steps can be taken from console input
		int step = Engine.POWER_STEP;
		int vertAltiStep = VERT_ALTI_STEP;
		if (powerStep != null && powerStep > step)
		{
			step = powerStep;
			//Assuming height is linearly proportional to power
			vertAltiStep = (int) (powerStep/Engine.POWER_STEP);
		}
		
        switch(moveAct.toUpperCase()) 
        {
	        case "U":
	            moveUD(step);
	    		changeAltitude(vertAltiStep);
	            System.out.println(toString());
	            break;
	        case "D":
	            moveUD(-step);
	    		changeAltitude(-vertAltiStep);
	            System.out.println(toString());
	            break;
	        case "R":
	            moveRL(step);
	    		changeAltitude(-MOVING_ALTI_STEP);
	            System.out.println(toString());
	            break;
	        case "L":
	            moveRL(-step);
	    		changeAltitude(-MOVING_ALTI_STEP);
	            System.out.println(toString());
	            break;
	        case "F":
	            moveFB(step);
	    		changeAltitude(-MOVING_ALTI_STEP);
	            System.out.println(toString());
	            break;
	        case "B":
	            moveFB(-step);
	    		changeAltitude(-MOVING_ALTI_STEP);
	            System.out.println(toString());
	            break;
	        case "H":
	        	stabilize();
	            System.out.println(toString());
	            break;
	        case "T":
	            takeoff();
	            System.out.println(toString());
	            break;
	        case "N":
	            land();
	            System.out.println(toString());
	            break;
	        case "X":
	            shutdown();
	            break;
	        case "S":
	            System.out.println(toString());
	            break;
	        default:
	            System.err.println("Invalid Option: " + moveAct);
        }
	}

	public static void main(String[] args)
	{
    	if (args.length != 1)
    	{
            System.out.println("usage: Controller <n engines>");
            System.exit(1);
    	}
    	
    	try 
    	{
    		int n = Integer.parseInt(args[0]);
    		if (n < 2)
    		{
    			System.err.println("Drone should have atleast 2 engines");
                System.exit(1);
    		}
    		else if (n%2 != 0)
    		{
    			System.err.println("Drone should have even number of engines");
                System.exit(1);
    		}
    		
			DroneControl control = new DroneControl(n);
			control.start();
			
    		Scanner s = new Scanner(System.in); 
	        while(true) 
	        {
	        	String strSelected = s.nextLine();
	        	if (strSelected == null || strSelected.length() == 0)
	        		continue;
	        	
				System.out.println("You have selected: " + strSelected);
	        	try {
					if (strSelected.equalsIgnoreCase("help")) {
						System.out.println("Below is the list of drone commands:\n");
						System.out.println("\t U <power increment> = Up");
						System.out.println("\t D <power increment> = Down");
						System.out.println("\t R <power increment> = Right");
						System.out.println("\t L <power increment> = Left");
						System.out.println("\t F <power increment> = Forward");
						System.out.println("\t B <power increment> = Backward");
						System.out.println("\t H = Hover");
						System.out.println("\t T = TakeOff");
						System.out.println("\t N = Land");		//Only one with not first char
						System.out.println("\t S = Status");
						System.out.println("\t X = Shutdown");
					} else {
			        	String[] strParams = strSelected.split("\\s+");
			        	Integer powerStep = null;
			        	if (strParams.length > 1)
			        		powerStep = Integer.parseInt(strParams[1]);
						control.processMove(strParams[0], powerStep);
					}
	        	} catch (DistressException de) {
	        		System.err.println("Received distress signal from Drone: " + de.getMessage());
	        		control.processMove("N", null);	//Land
					System.exit(1);
	        	} catch (Exception e1) {
	        		System.err.println("Exception running command:" + e1.getMessage());
	        		e1.printStackTrace();
	        	}
	        }
	
		} catch (Exception e) {
			System.err.println("Fatal exception. Drone Controller EXITING..\n" + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}			
		
		
		
	}


}
