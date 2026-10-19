# Gamepad navigation

Launcher: D-pad traverses Compose focus, A or Enter confirms, B goes Back.
Touch and mouse remain available. Import receives initial focus; game
details start focused on Play. Library has 2/3/4 columns and list mode.

Game: gamepad buttons normally map to libretro input for players 1/2.
START (108), MODE (110) and code 188 are reserved as menu toggle hotkeys:
this makes hardware Start unavailable as a native SNES input; the touch
Start button remains playable. With the menu open, Compose handles arrows,
confirm and Back. Local gameplay is paused and previous pause state
restored on close. Online lockstep cannot safely pause one peer.

Device checks pending: physical controllers, analog focus, screen rotation,
Bluetooth reconnect, gamepad navigation outside Library and accessibility.
