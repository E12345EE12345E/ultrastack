# PvE Mode Outline

Implementation outline for the PvE gamemode system.

## Part 1: Selecting and unlocking PvE modes

The gamemode for PvE can be selected in game settings. While selected, the empty area below the gamemode selection button fills with a display of PvE levels. Each PvE level will show its thumbnail (an image representing the mode) and title. To the left and right display the 2 previous and 2 next levels in smaller thumbnails (with 5 total thumbnails on screen), and the player can jump to those levels by clicking its thumbnail.

If a level doesn't have a thumbnail texture set yet, display as a color with hue based on the level id (137*id mod 360) so testers can still tell levels apart.

All players will have the first PvE level unlocked by default. If they pass, their stored database unlocked levels count increases by 1 - this means they have the first and second level unlocked. If they pass again, they will have the first, second, and third levels unlocked. Levels unlock chronologically, so a simple integer count will be better than a boolean array for storing unlocked levels.

The room host can select any levels that they have unlocked. If a player has not unlocked a level that is selected for play, they can still play that level when the game starts - however, their stored database unlocked level count does not increase after victory, so they cannot "skip" levels by repeatedly passing a higher level. If a player does have the level unlocked for play, they will be able to unlock the next level upon victory as usual.

PvE modes should not support multiple local players. Local players connected in the room should be moved to spectator by default if the host sets the gamemode to PvE, leaving only one player per client.

The selection menu should also include a difficulty selector (if there are multiple difficulty entries for a level).

## Part 2: PvE Level Design

A level will follow this format:
1. (usually) A beginning grace period with set time (different time for each level)
2. Multiple "sections" with different pass criteria
3. A bossfight at the end

The levels will be stored as JSON. Level JSON will include each "section" in an array, and each "section" will have:
* Array of arrays of passing criteria (either time-based, score-based, or some other factor) referencing hardcoded enums (similar to artifact effect types). Stored as 2d array to combine criteria factors - for example, SCORE:2000 OR (SCORE:1000 AND TIME:30000) would be stored as [[{type:SCORE,v:2000}],[{type:SCORE,v:1000},{type:TIME,v:30000}]]. The outer array is for OR and the inner arrays are for AND. In this example, the player could instantly pass the section with 2000+ score, or pass the section with 1000+ score after 30000 or more milliseconds have passed. The beginning grace period will just be defined as a section with a single time criteria of n milliseconds for levels including it. Note: SCORE criteria means score gained during the section - it doesn't count total score, just the score gain from the current session.
* A timeout variable - denoting milliseconds before the section counts as failed and the players lose. Should double-check all criteria at the moment of the timeout. Using the previous example, timeout of 30000 would cause the player to either lose or win at 30 seconds, depending on if they have 1000+ score (pass) or less (fail).
* Array of "environment effect modifiers" for that level section. These include gravity, garbage send intervals, and so on. These all have default values that are overridden when defined in this array for this section. Garbage send intervals should be an empty array by default.
* Board display mode - BOARD_DEFAULT for default display, where the board is in the middle (single board) or two side-by-side (double board for 3-4 players); BOARD_BOSSFIGHT during bossfight sections, where the board (that is containing the client's player) is on the left and the boss is displayed on the right (the other board, if any, is hidden).
* The final bossfight should be stored as a "section" as well, with an empty passing criteria array (in this case, the boss will trigger the section as passed when defeated) and an environment effect modifier with bossid (default -1 for no boss, 0-n are bosses that have been programmed).

More context on garbage send intervals:

Each garbage send interval can be defined with time (ms between each garbage send), initial time (the state of the interval's time counter when that section is loaded), style (default, double hole, custom, etc), and amount (which sets that many lines of identical garbage rows, so holes are in the same randomized vertical column. if multiple lines of random columns should be added, the dev should just define multiple garbage send intervals with same timing and amount 1.)

Levels will also contain overarching JSON for the entire level:
* Board size, spawn positions, initial board tiles (on level start), allowedTiles=false tiles, for player counts 1-2.
* A difficulty rank value (expect between 1-100 for most levels, although some harder levels in future may be 100+) displayed on the level selection screen but not used for actual gameplay.

More context on the gamemode itself:

* The gamemode will split groups of 3 and 4 players into multiple boards containing just 1 and 2 players. For the most part, both boards will be displayed on the screen side-by-side simultaneously for both players and spectators.
* The gamemode will use the built-in scoring functions (similar to Character Score mode), saving the score for each board separately as well as globally. The SCORE criteria will be global (so if one board scores 1500 and the other 500 for a 2000 criteria, all boards pass). All other criteria should be global as well (since boards move on from section to section simultaneously).
* If a board is eliminated, they will stay eliminated for the rest of the level. Everyone still passes if at least one board completes all sections of the PvE mode.
* Bossfights aren't stored in JSON but rather fully written in game code and defined with an id number. The JSON for a level determines which boss id appears during that section.

## Part 3: Bossfights

During a bossfight, the boss will attempt to attack players, cycling through its attack pattern. The boss will have a certain amount of health, and score will decrease its health by that amount (similar to how meter increases work, later artifacts may have effects that increase damage percentage). The boss's attacks can be cancelled if enough damage is sent during the attack windup phase, which stuns the boss for some time before it resumes from the next attack on its pattern.

Variables:
* Boss: Stun time on interrupt (always the same for each boss)
* Attack-specific: Windup time, how much score to interrupt

The boss should have a ripple circle around it (during idle state) that increases in size and changes color smoothly (during windup state) and then quickly shrinks and changes color (into attack state) before going back to idle.

Note: Even instant attacks, such as adding garbage to board, should have an attack duration, so the animation can transition smoothly between states and feel like there is more feedback for the user.

## Part 4: Initial Levels

Create an initial level.
1. 10 second section
2. 4000 points, or 2500 points and 60 seconds, timeout loss at 60 seconds
3. 30 second section, garbage interval 1 line of default garbage every 4 seconds
4. Bossfight

The boss should just use the default placeholder character texture for now. The attack pattern should be:
1. Wait 5 seconds (idle)
2. Queue attack: windup 2 seconds, attack duration 1 second (instant attack, add 4 lines of garbage to board). windup interrupt damage is 800.

The boss only has one attack but it should be enough for testing. The boss should also have 10,000 health. Once the boss is defeated, the section should continue to next (since this is the last section, the level completes - other levels with multiple bosses may have more sections after, and then a different boss again later).

The level should be registered with just this difficulty, and with only T piece artifacts at a random level 2 or 3 being rolled by the loot table function.

## Part 5: Level difficulties and other definitions in code

Level JSONs are registered in code with a registration function. This function defines a level name, a level ID, and the JSON files used for that level's difficulties (multiple level JSONS can be used for a level, each similar thematically but corresponding to a different difficulty), as well as a runnable loot table function for that level's artifact rewards.