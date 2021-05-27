# fisk

A game simulating a real life fishing experience! With interaction and feedback inspired by real life fishing Fisk enables users to catch and collect fish. 

## How to catch fish

0. Go to the main game by pressing "Start fishing". The first time a user opens "Start fishing", they will automatically enter instruction mode.  

1. The game starts in an idle state. Hold the phone in your right hand, with the screen pointing left. 
 *During instruction mode, this motion will be illustrated by an animation in a pop up on the screen.* 

2. Pull the phone back as if preparing to throw an actual fishing rod.
*During instruction mode, this motion will be illustrated by an animation in a pop up on the screen.* 

3. "Cast" by performing a forward swinging motion with your phone.
   * If a sufficient acceleration was registered, the line will be cast.
   * If sufficient acceleration was *not* registered,the player can try to cast the line again.

4. The line is cast a certain distance and time, proportional to the acceleration of the swing. The sound of the line being casted will be heard, as well as numbers displaying the distance of the cast ticking upwards. 

5. A "splash" sound effect is played when the hook lands in the water, and a float will be displayed amongst the animated waves.
*During instruction mode; a instruction pop-up will congratulate the user on a sucessfull cast, instruct them on how to identify that a fish is near, and instruct them to wait until a fish approaches.*  

6. *During instruction mode; An instruction pop-up will explain that a fish is approaching and how to reel it in, including how to handle the fish trying to escape.* 

7. After a random amount of time (10-30s) a fish will approach. You may hear it splashing, and feel small vibrations as it nibbles on the bait. But to get it on the hook, you need to wait until it bites, which is indicated by a constant vibration as well as the float disappearing. If you are too quick to reel in, the fish will be scared of. If you wait to long however, the fish will get away with the bait. If you are successful, you proceed to the next step. Otherwise, you go back to the previous step.
 *During instruction mode; The user will be instructed if they reeled in too soon or too late for a successful catch.* 

8. Reel in the fish by pressing down and holding anywhere on the screen. If you leave the line slack (i.e. not holding the button) for too long, the fish will get away.

9. When reeling in, the line might tense up when the fish tries to escape. To counteract this, move the phone backwards towards the shoulder/ear while still touching the screen to keep reeling in. This will create slack in the line, enabling you to keep reeling in the fish. If this is not done correctly, the distance meter will tick upwards, the line will snap and the fish will get away. If done correctly, you move on to the next step. 
 *During instruction mode; An instruction pop-up will explain that the line snapped due to incorrect reeling technique if the reel in was not successful.* 

9. If you reel in the line entirely (0m) without the fish escaping or the line snapping, the fish will be caught. A pop-up with information about the fish will be displayed. The fish catalouge will be updated to display all caught fish, while un-caught fish remain black an unexplored in the catalouge. 
 *During instruction mode; An instruction pop-up will congratulate the user on their first fish, and explain that the tutorial now is finished. Instruction mode will be exited* 
 
 10. The user can continue playing and try to catch all the fish! 
