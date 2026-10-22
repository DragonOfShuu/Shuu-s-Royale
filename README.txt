
Shuu's FPS (mod id: shuusfps)
==============================

Shuu's FPS turns your Minecraft world into a fast, replayable shooter arena.
You drop in with a loadout, fight for position, manage limited lives, and play around a shrinking border.

This mod is built for players who want:
- Tight matches with clear win conditions
- Fast respawns and quick rounds
- Private server events with friends
- A mix of Battle Royale pressure and classic timed arena fights

Game Modes
==============================

1) Battle Royale (`/br`)
- Last player or team alive wins.
- The world border starts large, then shrinks in steps.
- You can configure war time, shrink amount, shrink timing, and lives.

2) Arena Timed (`/at`)
- Set a fixed match duration.
- Rack up kills before the timer ends.
- Winner selection uses match stats at the end of the round.

3) Arena Utilities (`/shuufps`)
- Manage arena backup and reset flow.
- Keep your map reusable between matches.

Quick Start
==============================

All commands below require operator permission (level 2+).
Run them in the Overworld.

1. Stand where you want arena center and initialize:
    `/br init <size> <warTime> <shrinkAmount> <shrinkTime> [lives]`

    Example:
    `/br init 300 90 25 45 3`

2. Start Battle Royale:
    `/br start`

3. Stop Battle Royale if needed:
    `/br stop`

4. Start Arena Timed for a quick fight:
    `/at start <durationSeconds>`

5. Stop Arena Timed if needed:
    `/at stop`

6. Make or restore arena backup:
    `/shuufps backup`
    `/shuufps reset`

Command Reference
==============================

`/br` (Battle Royale)
- `/br start`
   Starts Battle Royale with the saved arena config and game settings.

- `/br stop`
   Stops the current Battle Royale and restores gameplay state.

- `/br settings warTime <value>`
   Sets the delay (seconds) before border shrinking starts.

- `/br settings shrinkAmount <value>`
   Sets how many blocks the border shrinks each cycle.

- `/br settings shrinkTime <value>`
   Sets cycle duration (seconds) for each shrink.

- `/br settings lives <value>`
   Sets lives per player.

- `/br set center <size>`
   Saves your current location as arena center and sets arena size.

- `/br init <size>`
   Sets center/size and starts backup initialization.

- `/br init <size> <warTime> <shrinkAmount> <shrinkTime> [lives]`
   Sets center, game settings, and starts backup initialization in one command.

`/at` (Arena Timed)
- `/at start <durationSeconds>`
   Starts a timed match using the saved arena size and center.

- `/at stop`
   Stops the current Arena Timed match.

- `/at set center <size>`
   Saves your current location as arena center and sets arena size.

- `/at init <size>`
   Sets center/size and starts backup initialization.

`/shuufps` (Arena Utilities)
- `/shuufps backup`
   Captures and saves the current arena so you can reset later.

- `/shuufps reset`
   Restores the most recent arena backup.

Match Flow Tips
==============================

- Use a medium border (200 to 400) for 6 to 20 players.
- Keep war time short (60 to 120 seconds) to avoid slow starts.
- Use low lives (1 to 3) when you want higher tension.
- Run `/shuufps backup` after preparing your arena so every round starts clean.

Server Notes
==============================

- This mod is intended for multiplayer sessions and private server events.
- If backup or restore is already running, wait for it to finish before starting another operation.
- Do not start `/br` and `/at` at the same time.

Enjoy the chaos. Drop in, lock in, and fight for the win.
