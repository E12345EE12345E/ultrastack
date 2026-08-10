# PvE Mode Outline

Implementation outline for the PvE gamemode system.

## Part 1: Selecting and unlocking PvE modes

The gamemode for PvE can be selected in game settings. While selected, the empty area below the gamemode selection button fills with a display of PvE levels. Each PvE level will show its thumbnail (an image representing the mode) and title. To the left and right display the 2 previous and 2 next levels in smaller thumbnails (with 5 total thumbnails on screen), and the player can jump to those levels by clicking its thumbnail.

If a level doesn't have a thumbnail texture set yet, display as a color with hue based on the level id (137*id mod 360) so testers can still tell levels apart.

All players will have the first PvE level unlocked by default. If they pass, their stored database unlocked levels count increases by 1 - this means they have the first and second level unlocked. If they pass again, they will have the first, second, and third levels unlocked. Levels unlock chronologically, so a simple integer count will be better than a boolean array for storing unlocked levels.

The room host can select any levels that they have unlocked. If a player has not unlocked a level that is selected for play, they can still play that level when the game starts - however, their stored database unlocked level count does not increase after victory, so they cannot "skip" levels by repeatedly passing a higher level. If a player does have the level unlocked for play, they will be able to unlock the next level upon victory as usual.

## Part 2: 