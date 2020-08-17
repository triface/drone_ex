# drone_example
The input takes n number of engines
The roll is aligned (to y-axis) for the left and right movements.
The pitch is aligned (to x-axis) for the forward and backward movements.
The thrust is aligned (to z-axis) for the up and down movements.
Additional movements for TakeOff, Landing and Hovering.

Drone has these statuses: Ready, Off, Hovering, Moving.
Drone has to be Hovering before any roll, pitch and thrust actions.

Classes:
Engine: Defines a rotor. Has methods for adjusting power.
DroneControl: Defines a drone and has methods for movement (roll, pitch and thrust)
            Drone will not start if engine power is less than 5%.
            Has battery discharge timer that lowers 1 percent for every 5 seconds
            Altitude will drop in MOVING status for a roll or pitch.

No external libraries used. Just java 1.8.

To run: Pass the argument for n number of engines.
DroneControl 4

Then type "help" to see console commands:
	 U <power increment> = Up
	 D <power increment> = Down
	 R <power increment> = Right
	 L <power increment> = Left
	 F <power increment> = Forward
	 B <power increment> = Backward
	 H = Hover
	 T = TakeOff
	 N = Land
	 S = Status
	 X = Shutdown
   
For example: type U 16, to move the drone up with additional power than a predefined power of 5
