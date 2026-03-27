--------------------------------------------------------------------------------------
-- Sonic 1 Collision Display for Physics Guide v2.2
--------------------------------------------------------------------------------------
-- This script reads RAM data from Sonic 1 as you play, reading information about the 
-- states of the player, and of all objects currently active. This data is processed
-- and displayed back in an intuitive way to show hitboxes, solid object collision, 
-- and other information such as the state of objects, the variables of the player,
-- and more.
-- 
-- Differences to guide:
-- What you see using this overlay may differ to the handmade visuals on the guide. 
-- Such as:
--   Sensors are shown in colours according to their direction, rather than labelling 
--   them. So Sonic's A + B sensors will both be one colour, and when he is in wall
--   Mode, they will change to the colour for sensors facing that side.
--
-- The purpose of this script is to illustrate as you play the ideas and information 
-- put forth by the guide thus making it easier to understand. 

-- Credits:
-- By @LapperDev (Sensors, Hitboxes & Solid Objects) and @MercurySilver (Terrain & Misc Additions)
--------------------------------------------------------------------------------------

-----------------
--- Constants ---
-----------------
	
	-- General
	HORIZONTAL 				= 0;
	VERTICAL				= 1;
	
	-- Colours and opacities
	COLOUR_BLACK 			= "black";
	COLOUR_WHITE			= "white";
	COLOUR_GREEN_DARK		= {0, 160, 120};
	COLOUR_NONE				= {0, 0, 0, 0};
	
	COLOUR_TEXT_DECIMAL		= COLOUR_WHITE;
	COLOUR_TEXT_HEX			= {128, 255, 240};
	COLOUR_TEXT				= COLOUR_TEXT_DECIMAL;
	
	COLOUR_HITBOX 			= {};
	COLOUR_HITBOX.PLAYER 	= {255, 0, 255, 128};	-- Colour of hitboxes
	COLOUR_HITBOX.BADNIK	= {255, 0, 150, 128};
	COLOUR_HITBOX.HURT		= {255, 0, 0, 128};
	COLOUR_HITBOX.INCREMENT	= {150, 0, 255, 140};
	COLOUR_HITBOX.SPECIAL	= {60, 0, 255, 128};
	
	COLOUR_SIZE 			= {255, 255, 0, 128};	-- Colour of object sizes
	COLOUR_SOLID			= {0, 255, 0, 128};	-- Colour of solid boxes
	COLOUR_PLATFORM			= {3, 252, 211, 128};	-- Colour of platform surfaces
	COLOUR_PLATFORM_EDGES	= {3, 252, 211, 230};	
	COLOUR_TRIGGER			= {0, 255, 255, 128};	-- Colour of object triggers
	
	OPACITY_TILES			= {0.8, 0.65}
	COLOUR_TILES			= {};
	COLOUR_TILES.TOP 		= {0, 113, 235}
	COLOUR_TILES.SIDES		= {0, 255, 168}
	COLOUR_TILES.FULL		= {255, 255, 255};
	COLOUR_TILES.NONE		= {64, 64, 64}
	COLOUR_TILE_FLAG		= {0, 178, 178};
	 
	COLOUR_SENSOR 			= {};
	COLOUR_SENSOR.DOWN 		= {0, 240, 0};
	COLOUR_SENSOR.UP 		= {0, 174, 239};
	COLOUR_SENSOR.LEFT 		= {255, 56, 255};
	COLOUR_SENSOR.RIGHT 	= {255, 84, 84};
	COLOUR_SENSOR.ANCHOR 	= COLOUR_WHITE;
	
	COLOUR_SENSOR.A			= {0, 240, 0};
	COLOUR_SENSOR.B			= {56, 255, 162};
	COLOUR_SENSOR.C			= {0, 174, 239};
	COLOUR_SENSOR.D			= {255, 242, 56};
	COLOUR_SENSOR.E			= {255, 56, 255};
	COLOUR_SENSOR.F			= {255, 84, 84};
	
	OPACITY_DARKEN			= {0.25, 0.5, 0.75, 1}
	
	-- Player animations
	PLAYER_ANIMATION_NAMES = {
	[0] = "Walk", [1] = "Run", [2] = "Roll", [3] = "Roll Fast", 
	[4] = "Push", [5] = "Wait", [6] = "Balance", [7] = "Look Up", 
	[8] = "Crouch", [9] = "Warp 1", [10] = "Warp 2", [11] = "Warp 3", [12] = "Warp 4", 
	[13] = "Brake", [14] = "Float 1", [15] = "Float 2", [16] = "Spring", 
	[17] = "Hang", [18] = "Leap 1", [19] = "Leap 2", [20] = "Surf", [21] = "Breath", 
	[22] = "Burnt", [23] = "Drown", [24] = "Death", [25] = "Shrink", 
	[26] = "Hurt", [27] = "Slide", [28] = "Null", [29] = "Float 3", [30] = "Float 4"
	};
	
	-- Angle modes
	MODE_NAMES = {[0] = "Floor", [1] = "Right Wall", [2] = "Celing", [3] = "Left Wall"};
	
	-- Display text templates
	DISPLAY_TEXT_TEMPLATE = "== POS AND SPEED =="
	.. "\nX: %s"
	.. "\nY: %s"
	.. "\nX Spd: %s"
	.. "\nY Spd: %s"
	.. "\nG Spd: %s"
	--	.. "\n"
	.. "\n  == SENSORS =="
	.. "\n"
	.. "\n"
	.. "\n"
	--	.. "\n"
	.. "\n   == ANGLE =="
	.. "\nAngle: %s (%s')"
	.. "\nMode: %s" 
	--	.. "\n"
	.. "\n   == FLAGS =="
	.. "\nGrounded: %s"
	.. "\nOn Object: %s"
	.. "\nFacing: %s"
	.. "\nPushing: %s"
	.. "\nControl Lock: %s"
	.. "\nStick: %s"
	--	.. "\n"
	.. "\n  == ANIMATION =="
	.. "\nSprite: %s (%s)"
	.. "\nFrame: %s (%s)"
	.. "\nDuration: %s (%s)"
	.. "\nTimer: %s"
	
	-- Ram positions
	OFF_PAUSED 				= 0xFFFFF63A
	OFF_GAME_TIMER			= 0xFFFE04;
	OFF_CAMERA 				= 0xFFF700;
	OFF_PLAYER 				= 0xFFD000;
	OFF_OBJECTS 			= 0xFFD000;
		SIZE_OBJECT 			= 0x40;
	OFF_GAMEMODE      		= 0xFFF600;
	
	
	-- References to positions in the actual game code:
	
	-- VBlank
	EX_VBLANK 				= 0x00000B10; -- VBlank:
	
	-- Collision
	EX_VERTICAL_SENSOR 		= 0x00015136; -- FindFloor:
	EX_HORIZONTAL_SENSOR 	= 0x00015274; -- FindWall:
	EX_SOLID_OBJECT		 	= 0x00010154; -- Solid_ChkEnter:
	EX_SOLID_OBJECT_ON		= 0x0001006A; -- move.w	d1,d2    --(in SolidObject:)
	
	EX_PLAYER_HITBOX		= 0x0001B5C8; -- move.w	#$5F,d6  --(in ReactToItem:)
	EX_OBJECT_HITBOX		= 0x0001B62C; -- @proximity:    --(in ReactToItem:)
	
	EX_PLATFORM				= 0x00007ACA; -- PlatformObject:
	EX_PLATFORM_EXIT		= 0x00007C40; -- ExitPlatform2:
	EX_SLOPED_PLATFORM		= 0x00007B9E; -- SlopeObject:
	EX_SLOPED_STAND			= 0x00008BA8; -- SlopeObject2:
	EX_SLOPED_SOLID_OBJECT	= 0x0000B61C; -- SolidObject2F:
	EX_BRIDGE_PLATFORM 		= 0x00007AA8; -- lea	(v_player).w,a1 --(in Bri_Solid:)
	
	-- Player sensors
	EX_PLAYER_SENSOR_A		= 0x00014E10; -- move.w	(sp)+,d0  --(in Sonic_AnglePos:)
	EX_PLAYER_SENSOR_B		= 0x00014DE0; -- move.w	d1,-(sp)  --(in Sonic_AnglePos:)
	EX_PLAYER_SENSOR_A_L	= 0x00015052; -- move.w	d1,-(sp)  --(in Sonic_WalkVertL:)
	EX_PLAYER_SENSOR_B_L	= 0x00015084; -- move.w	(sp)+,d0  --(in Sonic_WalkVertL:)
	EX_PLAYER_SENSOR_A_C	= 0x00014FB0; -- move.w	d1,-(sp)  --(in Sonic_WalkCeiling:)
	EX_PLAYER_SENSOR_B_C	= 0x00014FE2; -- move.w	(sp)+,d0  --(in Sonic_WalkCeiling:)
	EX_PLAYER_SENSOR_A_R	= 0x00014F40; -- move.w	(sp)+,d0  --(in Sonic_WalkVertR:)
	EX_PLAYER_SENSOR_B_R	= 0x00014F12; -- move.w	d1,-(sp)  --(in Sonic_WalkVertR:)
	EX_PLAYER_SENSOR_A_AIR	= 0x00015532; -- move.w	(sp)+,d0  --(in Sonic_HitFloor:)
	EX_PLAYER_SENSOR_B_AIR	= 0x00015504; -- move.w	d1,-(sp)  --(in Sonic_HitFloor:)
	EX_PLAYER_SENSOR_E		= 0x000157CC; -- move.b	#$40,d2  --(in Sonic_HitWall:)
	EX_PLAYER_SENSOR_F		= 0x0001563A; -- move.b	#-$40,d2  --(in sub_14EB4:)
	EX_PLAYER_SENSOR_C		= 0x000156D2; -- move.w	(sp)+,d0  --(in Sonic_DontRunOnWalls:)
	EX_PLAYER_SENSOR_D		= 0x000156A0; -- move.w	d1,-(sp)  --(in Sonic_DontRunOnWalls:)
	
	-- Special
	EX_SWING_PLATFORM		= 0x00007BDA; -- Swing_Solid:
	EX_MONITOR_SOLID		= 0x0000AB18; -- Mon_SolidSides:
	EX_SOLID_WALL 			= 0x00009158; -- Obj44_SolidWall2:
	
-------------
--- Input ---
-------------
	
	-- Data
	INPUT_PREVIOUS 			= input.read();
	INPUT 					= input.read();
	INPUT_PRESS 			= input.read();
	
----------------
--- Controls ---
----------------

	OVERLAY_CONTROLS = {}
	OVERLAY_CONTROL_COUNT = 0;
	
	--------------------------------------------------------------------------------------
	-- ControlAdd(name, colour, options, selected, shortcut)
	--------------------------------------------------------------------------------------
	-- Adds an overlay control with it's name, colour, available options, selected option and shortcut
	--------------------------------------------------------------------------------------
	function ControlAdd(name, colour, options, selected, shortcut)
		-- Create control 
		local control = {name = name, colour = colour, options = options, selected = selected, shortcut = shortcut};
		control.label = control.name .. ": " .. control.shortcut;
		control.width = string.len(control.label) * 4;
		control.height = 7;
		
		-- Add control to list (reference by position/id)
		OVERLAY_CONTROLS[OVERLAY_CONTROL_COUNT +  1] = control;
		OVERLAY_CONTROL_COUNT = OVERLAY_CONTROL_COUNT + 1;

		return OVERLAY_CONTROL_COUNT;
	end

	--------------------------------------------------------------------------------------
	-- ControlChange(id)
	--------------------------------------------------------------------------------------
	-- Incriment state of a control
	--------------------------------------------------------------------------------------
	function ControlChange(id)
		-- Find control
		local control = OVERLAY_CONTROLS[id];

		-- Increment and wrap
		control.selected = control.selected + 1;
		options = control.options;
		
		if control.selected > #options then 
			control.selected = 1;
		end
	end

	--------------------------------------------------------------------------------------
	-- ControlGetState(id)
	--------------------------------------------------------------------------------------
	-- Gets the state of a control
	--------------------------------------------------------------------------------------
	function ControlGetState(id)
		-- Find control
		local control = OVERLAY_CONTROLS[id];

		--Return selected option
		return control.options[control.selected];
	end

	--------------------------------------------------------------------------------------
	-- ControlGetShortcut(id)
	--------------------------------------------------------------------------------------
	-- Gets the shortcut of a control
	--------------------------------------------------------------------------------------
	function ControlGetShortcut(id)
		-- Find control
		local control = OVERLAY_CONTROLS[id];

		--Return selected option
		return control.shortcut;
	end


	-- General Toggles
	ControlShowOverlay		= ControlAdd("Show/Hide Overlay", "white", {true, false}, 1, "Q"); -- Show overlay visuals
	ControlShowShortcuts	= ControlAdd("Show Shortcuts", "white", {true, false}, 2, "W"); -- Draw shortcuts
	ControlPlayerVariables	= ControlAdd("Player Variables", "white", {true, false}, 21, "E"); -- Draw current player variables
	ControlDarkening		= ControlAdd("Darkening", "white", {0, 25, 50, 75, 100}, 4, "R"); -- How much to darken the game
	ControlCameraBounds		= ControlAdd("Camera Bounds", "white", {true, false}, 2, "T"); -- Draw camera bounds
	ControlHexValues		= ControlAdd("Hex Values", "white", {true, false}, 2, "Y"); -- Are values displayed as hexidecimal

	-- Solid tiles (Terrain)
	ControlTerrain			= ControlAdd("<> Terrain", "white", {"None", "Plain", "Degrees", "Real"}, 2, "U"); -- Draw the solid tiles 

	-- Objects
	ControlHitboxes			= ControlAdd("Object Hitboxes", COLOUR_HITBOX.BADNIK, {true, false}, 1, "I"); -- Draw hitboxes
	ControlTriggers			= ControlAdd("Object Triggers", COLOUR_TRIGGER, {true, false}, 1, "O");	-- Draw triggers
	ControlSensors			= ControlAdd("Object Sensors", COLOUR_SENSOR.DOWN, {true, false}, 1, "P"); -- Draw sensor representations
	ControlSolidity			= ControlAdd("Object Solidity", COLOUR_SOLID, {true, false}, 1, "F"); -- Draw solid box representations
	ControlSize				= ControlAdd("Object Width/Height", COLOUR_SIZE, {true, false}, 2, "G"); -- Draw object size (Width Radius/Height Radius), this isn't always relevant and not all solid objects use this size (though, it is relevant for all object collision with terrain and player collision with objects)
	ControlInfo				= ControlAdd("Object Info", "white", {true, false}, 2, "H"); -- Draw object names and information like id, sub id, and animation frame
	ControlSmoothing		= ControlAdd("Smoothing (Effect)", "white", {true, false}, 2, "J"); -- Smoothed collision positions (inaccurate). 
		-- If disabled, you see collisions as they have truly occurred that frame. Sensors might shake a bit when walking on slopes, solidboxes and hit boxes may appear to lag behind moving objects (except for Sonic, but it will when he is standing on a moving object), this is because these checks occur before the object moves.
		-- If enabled, shows the sensors/boxes relative to the final screen position of the object, even if the collision check happened before the object moved (which is usually the case). Looks nicer, but isn't accurate.
	
------------------------
--- Global Variables ---
------------------------
	GameTimer = 0;		
	GameTimerPrevious = 0;
	
-----------------------
--- Input Functions ---
-----------------------

	--------------------------------------------------------------------------------------
	-- InputUpdate()
	--------------------------------------------------------------------------------------
	-- Updates input and registers initial presses.
	--------------------------------------------------------------------------------------
	function InputUpdate()
		-- Load inputs
		INPUT_PREVIOUS = copytable(INPUT);
		INPUT = input.read();
		
		-- Update press events
		for k, v in pairs(INPUT) do
			INPUT_PRESS[k] = nil;
			
			local v_prev = INPUT_PREVIOUS[k];
			if v_prev == nil and v == true then
				INPUT_PRESS[k] = true;		-- Key has been pressed
			else 
				INPUT_PRESS[k] = nil;		-- Key has not been pressed
			end
		end
	end
	
------------------------------------
--- Names Of All Sonic 1 Objects ---
------------------------------------
	OBJECT_NAMES = 
	{		
		[0x01] = "Sonic", 					[0x08] = "Water Splash", 			[0x09] = "SS Sonic", 				[0x0a] = "Drowning Countdown",
		[0x0b] = "Breaking Pole", 			[0x0c] = "Flapping Door", 			[0x0d] = "Endpost", 				[0x0e] = "Title Sonic",
		[0x0f] = "Press Start", 			[0x10] = "Null", 					[0x11] = "Log", 					[0x12] = "SYZ Light",
		[0x13] = "Lava Ball Maker", 		[0x14] = "Lava Ball", 				[0x15] = "Swinging Platform", 		[0x16] = "Harpoon",
		[0x17] = "GHZ Spike Bridge", 		[0x18] = "Platform", 				[0x19] = "Null", 					[0x1a] = "Collapsing Ledge",
		[0x1b] = "Water Surface", 			[0x1c] = "Scenery", 				[0x1d] = "Switch", 					[0x1e] = "Ball Hog", 
		[0x1f] = "Crabmeat",				[0x20] = "Cannonball", 				[0x21] = "HUD", 					[0x22] = "Buzz Bomber", 		
		[0x23] = "Buzz Bomber Projectile",	[0x24] = "Explosion", 				[0x25] = "Ring", 					[0x26] = "Item Monitor", 		
		[0x27] = "Explosion",				[0x2a] = "Small Door", 				[0x2b] = "Chopper", 				[0x2c] = "Jaws", 				
		[0x2d] = "Burrobot", 				[0x2e] = "PowerUp",					[0x2f] = "MZ Platform", 			[0x3F] = "Boss Explosion", 	
		[0x28] = "Small Animal", 			[0x29] = "Points",					[0x30] = "MZ Crusher", 				[0x31] = "MZ Spike Trap", 	
		[0x32] = "Button", 					[0x33] = "Push Block",				[0x34] = "Title Card", 				[0x35] = "Burning Grass", 	
		[0x36] = "Spikes", 					[0x37] = "Bouncing Ring",			[0x38] = "Shield", 					[0x39] = "Game Over", 		
		[0x3a] = "Got Through Card", 		[0x3b] = "Purple Rock",				[0x3c] = "Breakable Wall",			[0x3d] = "GHZ Boss",
		[0x3e] = "Egg Capsule",				[0x3f] = "Boss Explode",			[0x40] = "Motobug",					[0x41] = "Spring",
		[0x42] = "Newtron",					[0x43] = "Roller",					[0x44] = "GHZ Faux Wall",			[0x45] = "Sideways Spike Trap",
		[0x46] = "MZ Faux Bricks",			[0x47] = "Bumper",					[0x48] = "GHZ Boss Boulder",		[0x49] = "Waterfall Sound",
		[0x4a] = "Special Stage Entry",		[0x4b] = "Giant Ring",				[0x4c] = "Lava Geyser Maker",		[0x4d] = "Lava Geyser Maker",
		[0x4e] = "Wall Of Lava",			[0x4f] = "Null",					[0x50] = "Yadrin",					[0x51] = "Breakable Block",
		[0x52] = "Moving Block",			[0x53] = "Collapsing Floor",		[0x54] = "Lava Tag",				[0x55] = "Basaran",
		[0x56] = "Floating Blocks",			[0x57] = "Mace",					[0x58] = "Spike Ball",				[0x59] = "SLZ Elevator",
		[0x5a] = "SLZ Circling Platform",	[0x5b] = "Staircase",				[0x5c] = "Pylon",					[0x5d] = "Fan",
		[0x5e] = "Seesaw",					[0x5f] = "Bomb",					[0x60] = "Orbinaut",				[0x61] = "LZ Block",
		[0x62] = "Gargoyle",				[0x63] = "LZ Platform Conveyor",	[0x64] = "Bubbles",					[0x65] = "Waterfall",
		[0x66] = "Rotating Junction",		[0x67] = "Running Disc",			[0x68] = "Conveyor Belt",			[0x69] = "SBZ Spinning Platform",
		[0x6a] = "Saw",						[0x6b] = "SBZ Stomper and Door",	[0x6c] = "SBZ Vanishing Platform",	[0x6d] = "Flamethrower",
		[0x6e] = "Electrocuter",			[0x6f] = "SBZ Platform Conveyor",	[0x70] = "Girder Block",			[0x71] = "Invisible Barrier",
		[0x72] = "Teleporter",				[0x73] = "MZ Boss",					[0x74] = "MZ Boss Fire",			[0x75] = "SYZ Boss",
		[0x76] = "SYZ Boss Block",			[0x77] = "LZ Boss",					[0x78] = "Caterkiller",				[0x79] = "Checkpoint",
		[0x7a] = "SLZ Boss",				[0x7b] = "SLZ Boss Spikeball",		[0x7c] = "Ring Sparkle",			[0x7d] = "Hidden Points",
		[0x7e] = "Special Stage Results",	[0x7f] = "SS Result Chaos Emerald",	[0x80] = "Continue Screen",			[0x81] = "Continue Sonic",
		[0x82] = "Robotnik",				[0x83] = "SBZ Crumbling Floor",		[0x84] = "FZ Boss Cylinder",		[0x85] = "FZ Boss",
		[0x86] = "FZ Boss Electricity",		[0x87] = "Ending Sonic",			[0x88] = "Ending Emeralds",			[0x89] = "Ending STH",		
		[0x8a] = "Credits",					[0x8b] = "Try Again Robotnik",		[0x8c] = "Try Again Emerald",
	}
	
----------------------
--- Math Functions ---
----------------------

    --------------------------------------------------------------------------------------
    -- GetRegisterByte(reg)
    --------------------------------------------------------------------------------------
    -- Get the value in a register as a byte.
    --------------------------------------------------------------------------------------
    function GetRegisterByte(reg)
        return AND(memory.getregister(reg), 0xFF)
    end
	
    
    --------------------------------------------------------------------------------------
    -- GetRegisterWord(reg)
    --------------------------------------------------------------------------------------
    -- Get the value in a register as a word.
    --------------------------------------------------------------------------------------
    function GetRegisterWord(reg)
        return AND(memory.getregister(reg), 0xFFFF)
    end
    
    --------------------------------------------------------------------------------------
    -- GetRegisterAddress(reg)
    --------------------------------------------------------------------------------------
    -- Get the value in a register as a valid address (0x000000 - 0xFFFFFF).
    --------------------------------------------------------------------------------------
    function GetRegisterAddress(reg)
        return AND(memory.getregister(reg), 0xFFFFFF)
    end

	--------------------------------------------------------------------------------------
	-- GetBit(n, k)
	--------------------------------------------------------------------------------------
	-- Get specific bit from a value.
	--------------------------------------------------------------------------------------
	function GetBit(n, k)
		local mask = bit.lshift(1, k);
		local masked_n = bit.band(n, mask);
		return bit.rshift(masked_n, k);
	end
	
	--------------------------------------------------------------------------------------
	-- Round(num, numDecimalPlaces)
	--------------------------------------------------------------------------------------
	-- Round a value to an integer.
	--------------------------------------------------------------------------------------
	function Round(num, numDecimalPlaces)
		local mult = 10^(numDecimalPlaces or 0);
		return math.floor(num * mult + 0.5) / mult;
	end
	
	
----------------------
----Data Functions----
----------------------
	
	--------------------------------------------------------------------------------------
	-- TableShallowCopy(num)
	--------------------------------------------------------------------------------------
	-- Copies surface of table or value
	--------------------------------------------------------------------------------------
	function TableShallowCopy(orig)
		local orig_type = type(orig)
		local copy
		if orig_type == 'table' then
			copy = {}
			for orig_key, orig_value in pairs(orig) do
				copy[orig_key] = orig_value
			end
		else -- number, string, boolean, etc
			copy = orig
		end
		return copy
	end
	
------------------------
----String Functions----
------------------------

	--------------------------------------------------------------------------------------
	-- ProcessPreciseWord(value)
	--------------------------------------------------------------------------------------
	-- Converts precise decimal to hex word and sub pixel component string
	--------------------------------------------------------------------------------------
	function ProcessPreciseWord(value)

		local str = tostring(value)
		if ControlGetState(ControlHexValues) then
			local whole = math.floor(value)
			
			if whole == value then
				str = "$"..string.format("%04X", AND(whole, 0xFFFF));
			else
				local fractional = math.floor((value - whole) * 256);
				local whole_hex = string.format("%04X", AND(whole, 0xFFFF));
				local fractional_hex = string.format("%02X", fractional);
				str = "$"..whole_hex .. "-" .. fractional_hex
			end
		end
		return str
	end
	
	--------------------------------------------------------------------------------------
	-- ProcessWord(value)
	--------------------------------------------------------------------------------------
	-- Converts decimal to hex word string
	--------------------------------------------------------------------------------------
	function ProcessWord(value)
		local str = tostring(value)
		if ControlGetState(ControlHexValues) then
			str = "$"..string.format("%04X", AND(math.floor(value), 0xFFFF));
		end
		return str
	end
	
	--------------------------------------------------------------------------------------
	-- ProcessByte(value)
	--------------------------------------------------------------------------------------
	-- Converts decimal to hex byte string
	--------------------------------------------------------------------------------------
	function ProcessByte(value)
		local str = tostring(value)
		if ControlGetState(ControlHexValues) then
			str = "$"..string.format("%02X", AND(math.floor(value), 0xFF));
		end
		return str
	end
	
	--------------------------------------------------------------------------------------
	-- ProcessByteConsistent(value)
	--------------------------------------------------------------------------------------
	-- Converts decimal to hex byte string, keeping decimal to 2 digits also
	--------------------------------------------------------------------------------------
	function ProcessByteConsistent(value)
		local str = string.format("%02d", value)
		if ControlGetState(ControlHexValues) then
			str = "$"..string.format("%02X", AND(math.floor(value), 0xFF));
		end
		return str
	end
	
	--------------------------------------------------------------------------------------
	-- ProcessBooleanSpecific(value)
	--------------------------------------------------------------------------------------
	-- Converts boolean to specified string
	--------------------------------------------------------------------------------------
	function ProcessBooleanSpecific(value, t, f)
		if value > 0 or value == true then 
			return t; 
		end
		return f;
	end
	
	--------------------------------------------------------------------------------------
	-- ProcessBoolean(value)
	--------------------------------------------------------------------------------------
	-- Converts boolean to string "True" or "False"
	--------------------------------------------------------------------------------------
	function ProcessBoolean(value)
		return ProcessBooleanSpecific(value, "True", "False")
	end

----------------------
----Draw Functions----
----------------------
	
	--------------------------------------------------------------------------------------
	-- GameBox(x, y, w, h, col, outline)
	--------------------------------------------------------------------------------------
	-- Draw a box using width and height radiuses to be accurate to the game.
	--------------------------------------------------------------------------------------
	function GameBox(x, y, w, h, col, outline)
		gui.box(x - w - 1, y - h - 1, 
			x + w + 1, y + h + 1, col, outline);
	end
	
	--------------------------------------------------------------------------------------
	-- DrawObjectPosition(ob_x, ob_y)
	--------------------------------------------------------------------------------------
	-- Draw a stylised position for objects.
	--------------------------------------------------------------------------------------	
	function DrawObjectPosition(ob_x, ob_y)
		gui.line(ob_x - 1, ob_y, ob_x + 1, ob_y, COLOUR_WHITE);
		gui.line(ob_x, ob_y - 1, ob_x, ob_y + 1, COLOUR_WHITE);
		gui.pixel(ob_x, ob_y, COLOUR_BLACK);
	end
	
	--------------------------------------------------------------------------------------
	-- DrawObjectSensor(ob_x, ob_y, sensor, smooth, line)
	--------------------------------------------------------------------------------------
	-- Draw a stylised sensor for objects.
	--------------------------------------------------------------------------------------	
	function DrawObjectSensor(ob_x, ob_y, sensor, smooth, line)
		-- Get sensor position based on smoothing
		local sensor_x, sensor_y;
		if smooth then
			sensor_x = ob_x + sensor.x_rel;
			sensor_y = ob_y + sensor.y_rel;
		else
			sensor_x = sensor.x - Camera.x;
			sensor_y = (sensor.y - Camera.y);
		end
		
		-- Line from object position outwards
		if line then
			if sensor.orientation == VERTICAL then
				if sensor.flipped == false then
					gui.line(sensor_x, ob_y, sensor_x, sensor_y, COLOUR_SENSOR.DOWN);
				else
					gui.line(sensor_x, ob_y, sensor_x, sensor_y, COLOUR_SENSOR.UP);
				end
			else
				if sensor.flipped == false then
					gui.line(ob_x, sensor_y, sensor_x, sensor_y, COLOUR_SENSOR.RIGHT);
				else
					gui.line(ob_x, sensor_y, sensor_x, sensor_y, COLOUR_SENSOR.LEFT);
				end
			end
		end
		
		-- Anchor point
		gui.pixel(sensor_x, sensor_y, COLOUR_SENSOR.ANCHOR);
	end

---------------------------
--- Solidity Structures ---
---------------------------
	HitboxesTable = {};
	SensorsTable = {};
	SolidsTable = {};
	WalkingEdgesTable = {};
	SlopesTable = {};
	

	SENSORBUFFER = {}
	SENSORBUFFER.A = nil;
	SENSORBUFFER.B = nil;
	SENSORBUFFER.C = nil;
	SENSORBUFFER.D = nil;
	SENSORBUFFER.E = nil;
	SENSORBUFFER.F = nil;
	
--------------------------
--- Solidity Functions ---
--------------------------
	
	--------------------------------------------------------------------------------------
	-- LoadObjectSensor(orientation)
	--------------------------------------------------------------------------------------
	-- Load sensor information from data register.
	--------------------------------------------------------------------------------------	
	function LoadObjectSensor(orientation)
		-- Collect all sensor information
		local sensor_x = GetRegisterWord("d3");
		local sensor_y = GetRegisterWord("d2");
		local sensor_direction = memory.getregister("a3");
		local flipped = sensor_direction < 0;
		
		-- Collect information about the object casting the sensor
		local object = GetRegisterAddress("a0");
		local object_x = memory.readword(object + 0x08);
		local object_y = memory.readword(object + 0x0c);
		
		-- Correct flipped sensors
		-- Objects have erratic flipped sensors and you'll still see this, this is accurate and only a fix to the base game would correct this.
		if (flipped) then 
			if orientation == HORIZONTAL then
				sensor_x = XOR(sensor_x, 0xF);
			else 
				sensor_y = XOR(sensor_y, 0xF);
			end
		end
		
		-- Collect all information in a table
		local sensor_info = {}
		sensor_info.x = sensor_x;
		sensor_info.y = sensor_y;
		sensor_info.x_rel = sensor_x-object_x;
		sensor_info.y_rel = sensor_y-object_y;
		sensor_info.orientation = orientation;
		sensor_info.flipped = flipped;
		
		-- Submit
		SubmitObjectSolidity(sensor_info, SensorsTable, object - OFF_OBJECTS);
	end
	
	--------------------------------------------------------------------------------------
	-- LoadObjectSlope()
	--------------------------------------------------------------------------------------
	-- Load object slope information from data register.
	--------------------------------------------------------------------------------------	
	function LoadObjectSlope(push_radius, slope_type)
		-- Collect information about collision
		local slope_array = GetRegisterAddress("a2");
		local box_width =  GetRegisterWord("d1");
		local box_height = GetRegisterWord("d2");
		
		-- Collect information about the object casting the sensor
		local object = GetRegisterAddress("a0");
		local object_x = memory.readword(object + 0x08);
		local object_y = memory.readword(object + 0x0c);
		
		-- Collect all information in a table
		local slope_info = {}
		slope_info.x = object_x;
		slope_info.y = object_y;
		slope_info.size = box_width;
		slope_info.height = box_height;
		local array = {};
		for i = 1, slope_info.size, 1 do
			array[i] = memory.readbyte(slope_array + (i - 1));
		end
		slope_info.data = array;
		slope_info.offset = push_radius;
		slope_info.type = slope_type;
		
		-- Submit
		SubmitObjectSolidity(slope_info, SlopesTable, object - OFF_OBJECTS);
	end
	
	--------------------------------------------------------------------------------------
	-- SubmitObjectSolidity()
	--------------------------------------------------------------------------------------
	-- Submits a specified type of solidity information to a specified object.
	--------------------------------------------------------------------------------------	
	function SubmitObjectSolidity(data, solidtype, object)
		-- Assign solid to object
		local current_object = solidtype[object];
		if current_object == nil then
			solidtype[object] = {[1] = data};
		else
			current_object[#current_object + 1] = data;
		end
	end
	
	--------------------------------------------------------------------------------------
	-- LoadSensorDistance()
	--------------------------------------------------------------------------------------
	-- Load object sensor distance information from data register.
	--------------------------------------------------------------------------------------	
	function LoadSensorDistance()
		-- Collect information about sensor distance
		local distance = GetRegisterByte("d1");
		if distance >= 128 then 
			distance = distance - 256;
		end
		return distance;
	end
	
-------------------------------------------------------------
--- Read solidity from data registers as code is executed ---
-------------------------------------------------------------
-- Here, the game code is read as it happens, collecting information about every sensor and solid object execution dynamically.

	-- Record vertical sensor
	memory.registerexec(EX_VERTICAL_SENSOR, function()
		LoadObjectSensor(VERTICAL);
	end);

	-- Record horizontal sensor
	memory.registerexec(EX_HORIZONTAL_SENSOR, function()
		LoadObjectSensor(HORIZONTAL);
	end);
	
	-- Record player hitbox
	memory.registerexec(EX_PLAYER_HITBOX, function()
		if ControlGetState(ControlHitboxes) then
			-- Collect information about hitbox
			local box_left = GetRegisterWord("d2");
			local box_top = GetRegisterWord("d3");
			local hitbox_width = GetRegisterWord("d4") / 2;
			local hitbox_height = GetRegisterWord("d5") / 2;
			
			-- Collect information about the object hitbox being checked
			local object = GetRegisterAddress("a0");
			local object_x = memory.readword(object + 0x08);
			local object_y = memory.readword(object + 0x0c);
			
			-- Collect all information in a table
			local hitbox_info = {}
			hitbox_info.x = box_left + hitbox_width;
			hitbox_info.y = box_top + hitbox_height;
			hitbox_info.x_rel = (box_left + hitbox_width) - object_x;
			hitbox_info.y_rel = (box_top + hitbox_height) - object_y;
			hitbox_info.width = hitbox_width;
			hitbox_info.height = hitbox_height;
			
			-- Submit
			SubmitObjectSolidity(hitbox_info, HitboxesTable, object - OFF_OBJECTS);
		end
	end);
	
	-- Record object hitbox
	memory.registerexec(EX_OBJECT_HITBOX, function()
		if ControlGetState(ControlHitboxes) then
			-- Collect information about the object hitbox being checked
			local object = GetRegisterAddress("a1");
			local object_x = memory.readword(object + 0x08);
			local object_y = memory.readword(object + 0x0c);
			
			-- Get the hitbox size being used by object
			local hitbox_data = memory.readbyte(object + 0x20);
			local touch_index = AND(hitbox_data, 0x3F) - 1; -- Get index of hitbox size array
			local touch_response = bit.rshift(AND(hitbox_data, 0xC0), 6); -- Get type of response
			local address = 0x1B5E4 + (touch_index * 2); -- Get size at index
			local hitbox_width = memory.readbyte(address);
			local hitbox_height = memory.readbyte(address + 1);
			
			-- Collect all information in a table
			local hitbox_info = {}
			hitbox_info.x = object_x;
			hitbox_info.y = object_y;
			hitbox_info.x_rel = 0;
			hitbox_info.y_rel = 0;
			hitbox_info.width = hitbox_width;
			hitbox_info.height = hitbox_height;
			hitbox_info.response = touch_response;
			
			-- Submit
			SubmitObjectSolidity(hitbox_info, HitboxesTable, object - OFF_OBJECTS);
		end
	end);
	
	-- Record solid object boxes
	memory.registerexec(EX_SOLID_OBJECT, function()
		if ControlGetState(ControlSolidity) then
			-- Collect information about collision
			local box_x = GetRegisterWord("d4");
			local box_width = GetRegisterWord("d1") - 11; -- We subtract 11 here, objects add 11 which is the Player's push radius + 1 to make the code more efficient, but for visual purposes we don't need to see that.
			local box_height = GetRegisterWord("d2"); -- This is the height used for when sonic is not standing on the object, and the object is searching for him. When Sonic is standing on the object, it uses a seperate radius which is usually 1px bigger or the exact same.
			-- This is all because of a bug in the code that keeps sonic standing on objects which DOESN'T add 1 when it's meant to. 
			-- So the solid box sizes seen are only those when not standing on the object.
			
			-- Sometimes these sizes can differ from the width and height radius used, so these sizes here will override any other when viewed in this overlay. Non-solid objects like a badnik will show it's Width and Height Radius as normal.
			
			-- Collect information about the object acting solid
			local object = GetRegisterAddress("a0");
			local object_x = memory.readword(object + 0x08);
			local object_y = memory.readword(object + 0x0c);
			
			-- Collect all information in a table
			local box_info = {};
			box_info.x = box_x;
			box_info.y = object_y;
			box_info.x_rel = box_x - object_x;
			box_info.y_rel = 0;
			box_info.width = box_width;
			box_info.height = box_height;
			box_info.type = "Solid";
			
			-- Submit
			SubmitObjectSolidity(box_info, SolidsTable, object - OFF_OBJECTS);
		end
	end);
	
	-- Record solid wall boxes
	memory.registerexec(EX_SOLID_WALL, function()
		if ControlGetState(ControlSolidity) then
			-- Collect information about collision
			local box_width = GetRegisterWord("d1") - 11;
			local box_height = GetRegisterWord("d2");
			
			-- Collect information about the object acting solid
			local object = GetRegisterAddress("a0");
			local object_x = memory.readword(object + 0x08);
			local object_y = memory.readword(object + 0x0c);
			
			-- Collect all information in a table
			local box_info = {};
			box_info.x = object_x;
			box_info.y = object_y;
			box_info.x_rel = 0;
			box_info.y_rel = 0;
			box_info.width = box_width;
			box_info.height = box_height;
			box_info.type = "Solid";
			
			-- Submit
			SubmitObjectSolidity(box_info, SolidsTable, object - OFF_OBJECTS);
		end
	end);
	
	-- Record platform collision
	memory.registerexec(EX_PLATFORM, function()
		if ControlGetState(ControlSolidity) then
			-- Collect information about collision
			local box_width = GetRegisterWord("d1");
			local box_height = 8;
			
			-- Collect information about the object acting solid
			local object = GetRegisterAddress("a0");
			local object_x = memory.readword(object + 0x08);
			local object_y = memory.readword(object + 0x0c);
			
			-- Collect all information in a table
			platform_info = {};
			platform_info.x = object_x;
			platform_info.y = object_y;
			platform_info.x_rel = 0;
			platform_info.y_rel = 0;
			platform_info.width = box_width;
			platform_info.height = box_height;
			platform_info.type = "Platform";
			
			-- Submit
			SubmitObjectSolidity(platform_info, SolidsTable, object - OFF_OBJECTS);
		end
	end);
	
	-- Record bridge collision
	memory.registerexec(EX_BRIDGE_PLATFORM, function()
		if ControlGetState(ControlSolidity) then
			-- Collect information about collision
			local box_width = GetRegisterWord("d2") / 2;
			local box_height = 8;
			
			-- Collect information about the object acting solid
			local object = GetRegisterAddress("a0");
			local object_x = memory.readword(object + 0x08);
			local object_y = memory.readword(object + 0x0c);
			
			-- Collect all information in a table
			platform_info = {};
			platform_info.x = object_x - 8; --Bridge code does it's own unique x check which adjusts for the offset of the bridge log doing the check
			platform_info.y = object_y;
			platform_info.x_rel = - 8;
			platform_info.y_rel = 0;
			platform_info.width = box_width;
			platform_info.height = box_height;
			platform_info.type = "Platform";
			
			-- Submit
			SubmitObjectSolidity(platform_info, SolidsTable, object - OFF_OBJECTS);
		end
	end);
	
	-- Record size of the area the Player's position can walk on
	memory.registerexec(EX_PLATFORM_EXIT, function()
		if ControlGetState(ControlSolidity) then
			-- Collect information about collision
			local box_x_off = GetRegisterWord("d1");
			local box_width = GetRegisterWord("d2");
			local box_height = 8;
			
			-- Collect information about the object acting solid
			local object = GetRegisterAddress("a0");
			local object_x = memory.readword(object + 0x08);
			local object_y = memory.readword(object + 0x0c);
			
			-- Collect all information in a table
			local edges_info = {}
			edges_info.x = object_x;
			edges_info.y = object_y;
			edges_info.x_offset = box_x_off;
			edges_info.width = box_width;
			
			-- Submit
			SubmitObjectSolidity(edges_info, WalkingEdgesTable, object - OFF_OBJECTS);
		end
	end);
	
	-- Record size of area sonic's position can walk on
	memory.registerexec(EX_SOLID_OBJECT_ON, function()
		if ControlGetState(ControlSolidity) then
			-- Collect information about collision
			local box_x = GetRegisterWord("d4");
			local box_width = GetRegisterWord("d1");
			
			-- Collect information about the object acting solid
			local object = GetRegisterAddress("a0");
			local object_x = memory.readword(object + 0x08);
			local object_y = memory.readword(object + 0x0c);
			
			-- Collect all information in a table
			local edges_info = {}
			edges_info.x = box_x;
			edges_info.y = object_y;
			edges_info.x_offset = box_width;
			edges_info.width = box_width;
			
			-- Submit
			SubmitObjectSolidity(edges_info, WalkingEdgesTable, object - OFF_OBJECTS);
		end
	end);
	
	-- Record item montitor solidity
	memory.registerexec(EX_MONITOR_SOLID, function()
		if ControlGetState(ControlSolidity) then
			-- Collect information about collision
			local box_width = GetRegisterWord("d1") - 11;
			local box_height = GetRegisterWord("d2");
			
			-- Collect information about the object acting solid
			local object = GetRegisterAddress("a0");
			local object_x = memory.readword(object + 0x08);
			local object_y = memory.readword(object + 0x0c);
			
			-- Collect all information in a table
			box_info = {};
			box_info.x = object_x;
			box_info.y = object_y;
			box_info.x_rel = 0;
			box_info.y_rel = 0;
			box_info.width = box_width;
			box_info.height = box_height;
			box_info.type = "Solid";
			
			-- Submit
			SubmitObjectSolidity(box_info, SolidsTable, object - OFF_OBJECTS);
		end
	end);
	
	-- Record swing platform solidity
	memory.registerexec(EX_SWING_PLATFORM, function()
		if ControlGetState(ControlSolidity) then
			-- Collect information about collision
			local box_width = GetRegisterWord("d1");
			local box_height = 8;
			
			-- Collect information about the object acting solid
			local object = GetRegisterAddress("a0");
			local object_x = memory.readword(object + 0x08);
			local object_y = memory.readword(object + 0x0c);
			
			-- Collect all information in a table
			platform_info = {};
			platform_info.x = object_x;
			platform_info.y = object_y;
			platform_info.x_rel = 0;
			platform_info.y_rel = 0;
			platform_info.width = box_width;
			platform_info.height = box_height;
			platform_info.type = "Platform";
			
			-- Submit
			SubmitObjectSolidity(platform_info, SolidsTable, object - OFF_OBJECTS);
		end
	end);
	
	-- Record object slope
	memory.registerexec(EX_SLOPED_PLATFORM, function()
		if ControlGetState(ControlSolidity) then
			LoadObjectSlope(0, "Platform");
		end
	end);
	memory.registerexec(EX_SLOPED_STAND, function()
		if ControlGetState(ControlSolidity) then
			LoadObjectSlope(0, "Stand");
		end
	end);
	memory.registerexec(EX_SLOPED_SOLID_OBJECT, function()
		if ControlGetState(ControlSolidity) then
			LoadObjectSlope(11, "Solid");
		end
	end);
	
	-- Record player floor sensor distances while grounded
	memory.registerexec(EX_PLAYER_SENSOR_A, function()
		SENSORBUFFER.A = LoadSensorDistance();
	end);
	memory.registerexec(EX_PLAYER_SENSOR_B, function()
		SENSORBUFFER.B = LoadSensorDistance();
	end);
	
	memory.registerexec(EX_PLAYER_SENSOR_A_R, function()
		SENSORBUFFER.A = LoadSensorDistance();
	end);
	memory.registerexec(EX_PLAYER_SENSOR_B_R, function()
		SENSORBUFFER.B = LoadSensorDistance();
	end);
	
	memory.registerexec(EX_PLAYER_SENSOR_A_C, function()
		SENSORBUFFER.A = LoadSensorDistance();
	end);
	memory.registerexec(EX_PLAYER_SENSOR_B_C, function()
		SENSORBUFFER.B = LoadSensorDistance();
	end);
	
	memory.registerexec(EX_PLAYER_SENSOR_A_L, function()
		SENSORBUFFER.A = LoadSensorDistance();
	end);
	memory.registerexec(EX_PLAYER_SENSOR_B_L, function()
		SENSORBUFFER.B = LoadSensorDistance();
	end);
	
	-- Record player wall sensor distances
	memory.registerexec(EX_PLAYER_SENSOR_E, function()
		SENSORBUFFER.E = LoadSensorDistance();
	end);
	memory.registerexec(EX_PLAYER_SENSOR_F, function()
		SENSORBUFFER.F = LoadSensorDistance();
	end);
	
	-- Record player floor sensor distances while airborne
	memory.registerexec(EX_PLAYER_SENSOR_A_AIR, function()
		SENSORBUFFER.A = LoadSensorDistance();
	end);
	memory.registerexec(EX_PLAYER_SENSOR_B_AIR, function()
		SENSORBUFFER.B = LoadSensorDistance();
	end);
	
	-- Record player ceiling sensor distances while airborne
	memory.registerexec(EX_PLAYER_SENSOR_C, function()
		SENSORBUFFER.C = LoadSensorDistance();
	end);
	
	memory.registerexec(EX_PLAYER_SENSOR_D, function()
		SENSORBUFFER.D = LoadSensorDistance();
	end);

-----------------
--- Misc Data ---
-----------------

	GamePaused = 0;
	
-------------------
--- Camera Data ---
-------------------

	Camera = {}
	
	--------------------------------------------------------------------------------------
	-- LoadCamera()
	--------------------------------------------------------------------------------------
	-- Load all information about the camera scrolling.
	--------------------------------------------------------------------------------------	
	function LoadCamera()
		Camera.x = memory.readword(OFF_CAMERA);
		Camera.y = memory.readword(OFF_CAMERA + 0x04);
	end
	
	LoadCamera();

-------------------
--- Player Data ---
-------------------

	Player = {}
	
	--------------------------------------------------------------------------------------
	-- LoadPlayer()
	--------------------------------------------------------------------------------------
	-- Load all information about the player object.
	--------------------------------------------------------------------------------------	
	function LoadPlayer()
		-- Position
		Player.x = memory.readword(OFF_PLAYER + 0x08);
		Player.y = memory.readword(OFF_PLAYER + 0x0c);
		Player.x_subpixel = memory.readbyte(OFF_PLAYER + 0x0a);
		Player.y_subpixel = memory.readbyte(OFF_PLAYER + 0x0e);
		Player.x_precise = Player.x + (Player.x_subpixel / 256);
		Player.y_precise = Player.y + (Player.y_subpixel / 256);
		Player.screen_x = Player.x - Camera.x;
		Player.screen_y = (Player.y - Camera.y);
		
		-- Angle
		Player.angle_previous = Player.angle;
		if Player.angle_previous == nil then 
			Player.angle_previous = 0; 
		end
		Player.angle = memory.readbyte(OFF_PLAYER + 0x26);
		
		-- Sizes
		Player.radius_push = 10;
		Player.radius_x = memory.readbytesigned(OFF_PLAYER + 0x17);
		Player.radius_y = memory.readbytesigned(OFF_PLAYER + 0x16);
		
		-- X and y speeds
		Player.xspeed = memory.readbytesigned(OFF_PLAYER + 0x10);
		Player.xspeed_subpixel = memory.readbyte(OFF_PLAYER + 0x11);
		Player.xspeed = Player.xspeed + (Player.xspeed_subpixel / 256)
		Player.yspeed = memory.readbytesigned(OFF_PLAYER + 0x12);
		Player.yspeed_subpixel = memory.readbyte(OFF_PLAYER + 0x13);
		Player.yspeed = Player.yspeed + (Player.yspeed_subpixel / 256)
		
		-- Groundspeed
		Player.groundspeed = memory.readbytesigned(OFF_PLAYER + 0x14);
		Player.groundspeed_subpixel = memory.readbyte(OFF_PLAYER + 0x15);
		Player.groundspeed = Player.groundspeed + (Player.groundspeed_subpixel / 256);
		
		-- Control Lock
		Player.controllock = memory.readword(OFF_PLAYER + 0x3E);
		Player.sticktoconvex = memory.readbyte(OFF_PLAYER + 0x38);
		
		-- Status
		Player.status = memory.readbyte(OFF_PLAYER + 0x22);
		Player.facingleft = GetBit(Player.status, 0);
		Player.inair = GetBit(Player.status, 1);
		Player.spinning = GetBit(Player.status, 2);
		Player.onobject = GetBit(Player.status, 3);
		Player.rolljumping = GetBit(Player.status, 4);
		Player.pushing = GetBit(Player.status, 5);
		Player.underwater = GetBit(Player.status, 6);
		
		-- Frame
		Player.animation = memory.readbyte(OFF_PLAYER + 0x1C);
		Player.animation_frame_display = memory.readbyte(OFF_PLAYER + 0x1A);
		Player.animation_frame = memory.readbyte(OFF_PLAYER + 0x1B);
		Player.animation_frame_timer_previous = Player.animation_frame_timer;
		Player.animation_frame_timer = memory.readbyte(OFF_PLAYER + 0x1E);
		if Player.animation_frame_duration == nil then Player.animation_frame_duration = 0; end
		if Player.animation_frame_timer_previous ~= nil then
			if Player.animation_frame_timer > Player.animation_frame_timer_previous then
				Player.animation_frame_duration = Player.animation_frame_timer;
			end
		end
		
		-- Sensors
		Player.sensor_a = SENSORBUFFER.A;
		Player.sensor_b = SENSORBUFFER.B;
		Player.sensor_c = SENSORBUFFER.C;
		Player.sensor_d = SENSORBUFFER.D;
		Player.sensor_e = SENSORBUFFER.E;
		Player.sensor_f = SENSORBUFFER.F;
		
		-- Get player mode
		-- No mode variable is stored ingame, so we use the same calculation used for floor sensors (slightly different ranges are used when deciding wall sensor mode)
		-- Based on previous frame's angle because that is the angle used for collision this frame, before the angle is changed
		local ang = 255 - Player.angle_previous;
		Player.mode = 0;
		if Player.inair == 0 then 
			if ang >= 1 and ang <= 32 then 
				Player.mode = 0;
			elseif ang >= 33 and ang <= 95 then 
				Player.mode = 1;
			elseif ang >= 96 and ang <= 160 then 
				Player.mode = 2;
			elseif ang >= 161 and ang <= 223 then 
				Player.mode = 3;
			elseif ang >= 224 and ang <= 255 then 
				Player.mode = 0;
			end
		end
	end
	
	LoadPlayer()

-------------------
--- Object Data ---
-------------------

	Objects = {}
	
	--------------------------------------------------------------------------------------
	-- LoadObjects()
	--------------------------------------------------------------------------------------
	-- Load all information about all player objects.
	--------------------------------------------------------------------------------------	
	function LoadObjects()

		-- Loop through currently available objects
		for i = 1, 128, 1 do
			-- Ram position
			local object_base = OFF_OBJECTS + (SIZE_OBJECT * (i - 1));
			
			-- Populate object information
			local current_object = {}
			
			-- Base
			current_object.index = SIZE_OBJECT * (i - 1);
			current_object.base = object_base;
			
			-- Id
			current_object.id = memory.readbyte(object_base + 0x00);
			current_object.sub_id = memory.readbyte(object_base + 0x28);
			
			-- Name
			local name = OBJECT_NAMES[current_object.id]
			if name == nil then 
				current_object.name = "";
			else 
				current_object.name = name;
			end
			
			-- Position
			current_object.x = memory.readword(object_base + 0x08);
			current_object.y = memory.readword(object_base + 0x0C);
			current_object.screen_x = current_object.x-Camera.x;
			current_object.screen_y = (current_object.y-Camera.y);
			
			-- Size
			current_object.width_radius = memory.readbyte(object_base + 0x17);
			current_object.height_radius = memory.readbyte(object_base + 0x16);
			if current_object.width_radius == 0 then 
				current_object.width_radius = memory.readbyte(object_base + 0x19); --use secondary width if width radius isnt being used
			end
			
			-- Triggers
			current_object.trigger = false;
			current_object.trigger_left = 0;
			current_object.trigger_width = 0;
			current_object.trigger_top = 0;
			current_object.trigger_height = 0;
			
			-- Collision Type
			current_object.collision_type = memory.readbyte(object_base + 0x20);
			
			-- Graphics
			current_object.gfx = memory.readbyte(object_base + 0x02);
			current_object.ani_frame = memory.readbyte(object_base + 0x1a);
			
			current_object.render = memory.readbyte(object_base + 0x01);
			current_object.flipped_x = GetBit(current_object.render, 0);
			current_object.flipped_y = GetBit(current_object.render, 1);
			
			-- Add information which isn't baked into the object proporties (hard coded ranges aka Triggers)
			if current_object.name == "Hidden Points" then
				current_object.trigger = true;
				current_object.trigger_left = -16;
				current_object.trigger_width = 32;
				current_object.trigger_top = -16;
				current_object.trigger_height = 32;
			elseif current_object.name == "Checkpoint" then
				if current_object.sub_id ~= 0 then
					current_object.trigger = true;
					current_object.trigger_left = -8;
					current_object.trigger_width = 16;
					current_object.trigger_top = -64;
					current_object.trigger_height = 104;
				end
			elseif current_object.name == "Endpost" then
				current_object.trigger = true;
				current_object.trigger_left = 0;
				current_object.trigger_width = 32;
				current_object.trigger_top = -9999;
				current_object.trigger_height = 99999;
			elseif current_object.name == "Fan" then
				if current_object.flipped_x == 1 then
					current_object.trigger = true;
					current_object.trigger_left = -80;
					current_object.trigger_width = 240;
					current_object.trigger_top = -96;
					current_object.trigger_height = 112;
				else
					current_object.trigger = true;
					current_object.trigger_left = 80;
					current_object.trigger_width = -240;
					current_object.trigger_top = -96;
					current_object.trigger_height = 112;
				end
			elseif current_object.name == "Bubbles" then
				if memory.readbyte(object_base + 0x2E) ~= 0 then
					current_object.trigger = true;
					current_object.trigger_left = -16;
					current_object.trigger_width = 32;
					current_object.trigger_top = 0;
					current_object.trigger_height = 16;
				end
			end
			
			-- Find hitboxes belonging to this object
			local current_hitboxes = HitboxesTable[current_object.base - OFF_OBJECTS];
			if current_hitboxes ~= nil then
				current_object.hitboxes = current_hitboxes;
			else
				current_object.hitboxes = nil;
			end
			
			-- Find sensors belonging to this object
			local current_sensors = SensorsTable[current_object.base - OFF_OBJECTS];
			if current_sensors ~= nil then
				current_object.sensors = current_sensors;
			else
				current_object.sensors = nil;
			end
			
			-- Find solids belonging to this object
			local current_solids = SolidsTable[current_object.base - OFF_OBJECTS];
			if current_solids ~= nil then
				current_object.solids = current_solids;
			else
				current_object.solids = nil;
			end
			
			-- Find platform exits belonging to this object
			local current_walkedges = WalkingEdgesTable[current_object.base - OFF_OBJECTS];
			if current_walkedges ~= nil then
				current_object.walking_edges = current_walkedges;
			else
				current_object.walking_edges = nil;
			end
			
			-- Find slopes belonging to this object
			local current_slopes = SlopesTable[current_object.base - OFF_OBJECTS];
			if current_slopes ~= nil then
				current_object.slopes = current_slopes;
			else
				current_object.slopes = nil;
			end
			
			-- Add to objects
			Objects[i] = current_object;
		end
	end
	
	LoadObjects()
	
------------------------------------------
--- Build data for solid tiles display ---
------------------------------------------
	-- author: Mercury

	local solidImages = {
		[0] = { -- no solid
			[0] = {}; -- no flip
			[1] = {}; -- x flip
			[2] = {}; -- y flip
			[3] = {}; -- x and y flip
		};
		[1] = { -- top solid
			[0] = {}; -- no flip
			[1] = {}; -- x flip
			[2] = {}; -- y flip
			[3] = {}; -- x and y flip
		};
		[2] = { -- sides solid
			[0] = {}; -- no flip
			[1] = {}; -- x flip
			[2] = {}; -- y flip
			[3] = {}; -- x and y flip
		};
		[3] = { -- all solid
			[0] = {}; -- no flip
			[1] = {}; -- x flip
			[2] = {}; -- y flip
			[3] = {}; -- x and y flip
		};
	}
	
	local solidAngles = {}

	do -- build solid images
		local header = string.char(0xFF, 0xFE, 0x00, 0x10, 0x00, 0x10, 0x01, 0xFF, 0xFF, 0xFF, 0xFF); -- GD truecolor 16x16 image header
		local filled = string.char(0x00,  COLOUR_TILES.FULL[1], COLOUR_TILES.FULL[2], COLOUR_TILES.FULL[3]) -- GD ARGB 
		local empty = string.char(0x7F, 0x00, 0x00, 0x00); -- GD ARGB transparent black
		
		local function flipSolid(t, flipX, flipY)
			if flipX then
				local flip = { header; }
				for row = 2, 242, 16 do
					for pos = 0, 15 do
						flip[row + 15 - pos] = t[row + pos];
					end
				end
				t = flip;
			end
			if flipY then
				local flip = { header; }
				for row = 2, 242, 16 do
					for pos = 0, 15 do
						flip[pos + 244 - row] = t[pos + row];
					end
				end
				t = flip;
			end
			return t;
		end
		
		local function shadeSolid(t, col)
			local shade = string.char(0x00, col[1], col[2], col[3])
			local newSolid = { header; }
			for i = 2, #t do
				if t[i] == empty then
					newSolid[i] = empty
				else
					newSolid[i] = shade
				end
			end
			return newSolid
		end

		local address = 0x62A00; -- Solidity\Vertical
		local address_angle = 0x62900; -- Angles
		
		-- for number of solids
		for i = 0, 0xFF do
			local t = { header; } -- table to be concatenated into GD string
			
			for x = 0, 0xF do
				local top = 2 + x;
				local bottom = 242 + x;
				local h = memory.readbytesigned(address);
				address = address + 1;

				if h > 0 then
					local split = bottom - h * 16
					for i = top, bottom, 16 do
						t[i] = i > split and filled or empty;
					end
				elseif h < 0 then
					local split = top - h * 16
					for i = top, bottom, 16 do
						t[i] = i < split and filled or empty;
					end
				else
					for i = top, bottom, 16 do
						t[i] = empty;
					end
				end
			end
			
			do
				local t_x = flipSolid(t, true, false)
				local t_y = flipSolid(t, false, true)
				local t_xy = flipSolid(t, true, true)
				
				solidImages[0][0][i] = table.concat(shadeSolid(t, COLOUR_TILES.NONE))
				solidImages[0][1][i] = table.concat(shadeSolid(t_x, COLOUR_TILES.NONE));
				solidImages[0][2][i] = table.concat(shadeSolid(t_y, COLOUR_TILES.NONE));
				solidImages[0][3][i] = table.concat(shadeSolid(t_xy, COLOUR_TILES.NONE));
				
				solidImages[1][0][i] = table.concat(shadeSolid(t, COLOUR_TILES.TOP))
				solidImages[1][1][i] = table.concat(shadeSolid(t_x, COLOUR_TILES.TOP));
				solidImages[1][2][i] = table.concat(shadeSolid(t_y, COLOUR_TILES.TOP));
				solidImages[1][3][i] = table.concat(shadeSolid(t_xy, COLOUR_TILES.TOP));
				
				solidImages[2][0][i] = table.concat(shadeSolid(t, COLOUR_TILES.SIDES))
				solidImages[2][1][i] = table.concat(shadeSolid(t_x, COLOUR_TILES.SIDES));
				solidImages[2][2][i] = table.concat(shadeSolid(t_y, COLOUR_TILES.SIDES));
				solidImages[2][3][i] = table.concat(shadeSolid(t_xy, COLOUR_TILES.SIDES));
				
				solidImages[3][0][i] = table.concat(t)
				solidImages[3][1][i] = table.concat(t_x);
				solidImages[3][2][i] = table.concat(t_y);
				solidImages[3][3][i] = table.concat(t_xy);
			end
			
			solidAngles[i] = memory.readbyte(address_angle + i);
		end
	end

	-- draws set of all 255 solids on screen for debugging purposes
	local function drawSolidSet()
		local i = 0
		for y = 0, 224, 16 do
			for x = 0, 320, 16 do
				gui.image(x, y, solidImages[0][0][i], 0.5);
				i = i + 1
				if i == 255 then
					return;
				end
			end
		end
	end

	-- draws `chunk` at `x, y` (screen coordinates)
	local function drawChunk(x, y, chunk)
		local address = 0xFF0000 + chunk * 0x200;
		local collision_index_address = memory.readlong(0xFFF796);
		
		for o = 0, 15, 1 do
			for i = 0, 15, 1 do
				local tx = x + (i * 16);
				local ty = y + (o * 16);
				
				if ty > -16 and ty < 224 and tx > -16 and tx < 320 then -- only draw blocks in view
					local block = memory.readword(address);
					local coll = SHIFT(AND(block, 0x6000), 13);
					if coll > 0 then -- if block is flagged as solid
						local solid = memory.readbyte(collision_index_address + AND(block, 0x3FF));
						if solid > 0 then
							local flip = SHIFT(AND(block, 0x1800), 11);
							local image = solidImages[coll][flip][solid];
							
							-- Checkerboard opacity
							local opacity = XOR(i, o) % 2 == 0 and 1 or 2
							
							-- Draw tile
							gui.image(tx, ty, image, OPACITY_TILES[opacity]);
							
							-- Drawing the angle
							if ControlGetState(ControlTerrain) ~= "None" and ControlGetState(ControlTerrain) ~= "Plain" then
								local display_angle = ""
								local display_col = COLOUR_TEXT;
								
								local angle = solidAngles[solid]
								if angle ~= 0xFF then
									-- Modify angle based on flipped
									if flip == 1 then 
										-- Flipped X
										angle = 256 - angle;
									elseif flip == 2 then 
										-- Flipped Y
										angle = (128 + (256 - angle)) % 256;
									elseif flip == 3 then 
										-- Flipped Both
										angle = (angle + 128) % 256;
									end
									
									-- Display angle string
									if ControlGetState(ControlTerrain) == "Real" then
										-- Decimal
										display_angle = ProcessByteConsistent(angle);
									elseif ControlGetState(ControlTerrain) == "Degrees" then
										-- Degrees
										display_angle = tostring(Round((256-angle) * (360 / 256), 0));
									end
								else
									display_angle = "*";
									display_col = COLOUR_TILE_FLAG;
								end
								
								-- Draw angle text
								local w = math.floor((string.len(display_angle) * 4) /2)
								local h = math.ceil(7 /2)
								gui.text((tx+8)-w, (ty+8)-h, display_angle, display_col, "black")
							end
						end
					end
				end
				address = address + 2;
			end
		end
	end


	-- draws all chunks in view starting from `left, top` (zone coodinates)
	local function drawTerrain(left, top)
		
		local right = left + 320
		local bottom = top + 224
		
		local size = 0x100 -- size of a chunk (x or y)
		
		local l = left - (left % size)
		local t = top - (top % size)
		local r = right - (right % size)
		local b = bottom - (bottom % size)
		
		for y = t, b, size do
			for x = l, r, size do
				local lx = x / size
				local ly = y / size % 8
				
				local chunk = memory.readbyte(0xFFA400 + lx + ly * 0x80)
				
				if chunk > 0 then
					local index = AND(chunk, 0x7F) - 1
					local special = AND(chunk, 0x80) > 0
					if special and bit.band(memory.readbyte(0xFFD001), 0x40) > 0 then
						drawChunk(x - left, y - top, index + 1)
						--gui.text(x - left, y - top, "SPECIAL", "cyan", "black")
					else
						drawChunk(x - left, y - top, index)
					end
				end
			end
		end
	end

-----------------------
--- Update and Draw ---
-----------------------
	
	-- We draw upon execution of VBlank
	memory.registerexec(EX_VBLANK, function()
		
	--------------
	--- Checks ---
	--------------
	
		-- Are we in a zone?
		if memory.readbyte(OFF_GAMEMODE) ~= 0x0C then
			return
		end
		
		-- New frame?
		GameTimerPrevious = GameTimer;
		GameTimer = memory.readword(OFF_GAME_TIMER);
		
	-------------
	--- Input ---
	-------------
		InputUpdate()

	----------------
	--- Controls ---
	----------------	
		
		-- Process Controls
		for i, control in ipairs(OVERLAY_CONTROLS) do
			if INPUT_PRESS[control.shortcut] then
				ControlChange(i)
			end
		end

	------------
	--- Draw ---
	------------
		if ControlGetState(ControlShowOverlay) then
			
		--------------
		--- Darken ---
		--------------
			
			if ControlGetState(ControlDarkening) > 0 then
				gui.box(-1, -1, 320, 224, {0, 0, 0, (ControlGetState(ControlDarkening) / 100) * 255}, COLOUR_NONE);
			end
			
		----------------------------
		--- Loop through objects ---
		----------------------------
			for i = 1, 128, 1 do
				local object = Objects[i]
				-- Draw object
				if object.id ~= 0 then
					-- Size
					if ControlGetState(ControlSize) then
						if object.width_radius > 0 and object.height_radius > 0 then
							GameBox(object.screen_x, object.screen_y, object.width_radius, object.height_radius, COLOUR_SIZE, COLOUR_NONE);
						end
					end

					-- Hitboxes
					if ControlGetState(ControlHitboxes) and object.hitboxes ~= nil  then
						for i, hitbox in ipairs(object.hitboxes) do
							-- Type of hitbox
							local col = COLOUR_HITBOX.PLAYER; 
							if hitbox.response == 0 then 
								col = COLOUR_HITBOX.BADNIK; 
							elseif hitbox.response == 1 then 
								col = COLOUR_HITBOX.INCREMENT; 
							elseif hitbox.response == 2 then 
								col = COLOUR_HITBOX.HURT;								
							elseif hitbox.response == 3 then 
								col = COLOUR_HITBOX.SPECIAL;							
							end
							
							if ControlGetState(ControlSmoothing) then
								GameBox(object.screen_x + hitbox.x_rel, object.screen_y + hitbox.y_rel, hitbox.width, hitbox.height, col, COLOUR_NONE);
							else
								GameBox(hitbox.x - Camera.x, (hitbox.y - Camera.y), hitbox.width, hitbox.height, col, COLOUR_NONE);
							end
						end
					end
					
					-- Trigger
					if ControlGetState(ControlTriggers) and object.trigger then
						gui.box(object.screen_x + object.trigger_left - 1, object.screen_y + object.trigger_top - 1, 
							object.screen_x + object.trigger_left + object.trigger_width, object.screen_y + object.trigger_top + object.trigger_height, COLOUR_TRIGGER, COLOUR_NONE);
					end
					
					-- Sensors
					if ControlGetState(ControlSensors) and object.sensors ~= nil then
						for i, sensor in ipairs(object.sensors) do
							DrawObjectSensor(object.screen_x, object.screen_y, sensor, ControlGetState(ControlSmoothing), 1);
						end
					end
					
					-- Solids
					if ControlGetState(ControlSolidity) then
						-- Normal Solids
						if object.solids ~= nil then
							for i, solid in ipairs(object.solids) do
								local col = COLOUR_SOLID;
								if solid.type == "Platform" then 
									col = COLOUR_PLATFORM; 
								end
								
								if ControlGetState(ControlSmoothing) then
									GameBox(object.screen_x + solid.x_rel, object.screen_y + solid.y_rel, solid.width, solid.height, col, COLOUR_NONE);
								else
									GameBox(solid.x - Camera.x, (solid.y - Camera.y)--[[0x800]], solid.width, solid.height, col, COLOUR_NONE);
								end
							end
						end
						
						-- Walking edges
						if object.walking_edges ~= nil and object.slopes == nil then
							for i, current_walkedges in ipairs(object.walking_edges) do
								
								local obj_x = current_walkedges.x - Camera.x;
								local obj_y = (current_walkedges.y - Camera.y);
								if ControlGetState(ControlSmoothing) then
									obj_x = object.screen_x;
									obj_y = object.screen_y;
								end
								
								local x1 = obj_x - current_walkedges.x_offset;
								local x2 = x1 + (current_walkedges.width * 2) - 1;
								gui.line(x1, Player.screen_y, x2, Player.screen_y, COLOUR_PLATFORM_EDGES);
							end
						end
						
						-- Slopes
						if object.slopes ~= nil then
							for i, current_slope in ipairs(object.slopes) do							
								local size = current_slope.size; -- X radius of sloped object, size of slope data by coincidence because slope data compressed
								
								local obj_x = current_slope.x - Camera.x;
								local obj_y = (current_slope.y - Camera.y);
								if ControlGetState(ControlSmoothing) then
									obj_x = object.screen_x;
									obj_y = object.screen_y;
								end

								-- What kind of slope to draw
								local col = COLOUR_PLATFORM;
								local thickness = 0;
								local y_offset = -Player.radius_y
								if current_slope.type == "Platform" then
									thickness = 16;
									y_offset = 0;
								elseif current_slope.type == "Solid" then
									thickness = current_slope.height * 2;
									y_offset = 0;
									col = COLOUR_SOLID; 
								end
								obj_y = obj_y + y_offset;
								
								-- Left to right or right to left
								local slope_start = obj_x - size;
								local slope_end = obj_x + size - 2;
								local slope_step = 2
								if object.flipped_x == 1 then
									slope_start = obj_x + size - 2;
									slope_end = obj_x - size;
									slope_step = -2;
								end
								
								-- Loop through x positions starting from right side of object to left
								local o = 1;
								for x = slope_start, slope_end, slope_step do
									if x - (obj_x - size) >= current_slope.offset 
										and (obj_x + size) - x >= current_slope.offset then
										gui.line(x, obj_y - current_slope.data[o], x, obj_y - current_slope.data[o] + thickness, col);
									end
									if (x + 1) - (obj_x - size) >= current_slope.offset 
										and (obj_x + size) - (x + 1) >= current_slope.offset then
										gui.line(x + 1, obj_y - current_slope.data[o], x + 1, obj_y - current_slope.data[o] + thickness, col);
									end
									o = o + 1; -- increment slope data position
								end
							end
						end
					end
					
					-- Details
					if ControlGetState(ControlInfo) then
						if object.screen_x > object.width_radius and object.screen_x - object.width_radius < 320 - string.len(object.name) * 5 then
							-- Get maximal sizes
							local large_width = math.max(8, object.width_radius);
							local large_height = math.max(8, object.height_radius);

							-- Id, sub id, graphics, and animation frame
							local y_offset = 8;
							gui.text(object.screen_x - large_width, object.screen_y - large_height - 8, ProcessByte(object.flipped_x) .. ", " .. ProcessByte(object.id) .. ", " .. ProcessByte(object.sub_id) .. ", " .. ProcessByte(object.gfx) .. ", " .. ProcessByte(object.ani_frame));
							y_offset = 16;
							
							-- Name
							gui.text(object.screen_x - large_width, object.screen_y - large_height - y_offset, object.name);
						end
					end
					
					-- Position
					DrawObjectPosition(object.screen_x, object.screen_y);
				end
			end
			
		--------------------
		--- Draw Terrain ---
		--------------------
			if ControlGetState(ControlTerrain) ~= "None" then
				drawTerrain(Camera.x, Camera.y);
			end

		--------------------------
		--- Draw Camera Bounds ---
		--------------------------
			if ControlGetState(ControlCameraBounds) then
				local x_centre = 160;
				local y_centre = 112 - 16;
				local x_min = 160 - 16;
				
				local draw_bottom = false;
				draw_col = "white";
				
				local ani = PLAYER_ANIMATION_NAMES[Player.animation]
				if ani == "Roll" or ani == "Roll Fast" then
					draw_bottom = true;
					draw_col = "magenta";
				end
				
				if draw_bottom then
					gui.line(x_min, y_centre, x_centre, y_centre, draw_col)
					gui.box(x_min, y_centre - 32, x_centre, y_centre + 32, COLOUR_NONE, draw_col)
					
					gui.line(x_min, y_centre + 5, x_centre, y_centre + 5, "white")
					gui.box(x_min, y_centre + 5 - 32, x_centre, y_centre + 5 + 32, COLOUR_NONE, "white")
				else
					gui.line(x_min, y_centre, x_centre, y_centre, draw_col)
					gui.box(x_min, y_centre - 32, x_centre, y_centre + 32, COLOUR_NONE, draw_col)
				end
			end
			
		----------------------
		--- Draw Variables ---
		----------------------
			if ControlGetState(ControlPlayerVariables) then
				-- Text display values
				gui.box(5, 5, 90, 224-5, {0, 0, 0, 200}, COLOUR_NONE) 

				-- Display string
				local display_text = DISPLAY_TEXT_TEMPLATE:format(
						ProcessPreciseWord(Player.x_precise)
					, ProcessPreciseWord(Player.y_precise)
					, ProcessPreciseWord(Player.xspeed)
					, ProcessPreciseWord(Player.yspeed)
					, ProcessPreciseWord(Player.groundspeed)
					, ProcessByte(Player.angle) , Round((256-Player.angle) * (360 / 256), 2) % 360
					, MODE_NAMES[Player.mode]

					, ProcessBoolean(1 - Player.inair)
					, ProcessBoolean(Player.onobject)
					, ProcessBooleanSpecific(Player.facingleft, "Left", "Right")
					, ProcessBoolean(Player.pushing)
					, ProcessByte(Player.controllock)
					, ProcessBoolean(Player.sticktoconvex)

					, PLAYER_ANIMATION_NAMES[Player.animation], ProcessByte(Player.animation)
					, ProcessByte(Player.animation_frame_display), ProcessByte(Player.animation_frame)
					, ProcessByte(Player.animation_frame_duration), ProcessByte(Player.animation_frame_duration + 1) -- Add 1 because the timer includes 0, so the true duration is actually 1 longer than the ingame value
					, ProcessByte(Player.animation_frame_timer)
				)
					
				gui.text(10, 10, display_text, COLOUR_TEXT, COLOUR_BLACK);
				
				-- Draw sensors
				local ty = 74 - 8;
				local spacing = 8;
				local str = "";
				if Player.sensor_a == nil then str = "--" else str = ProcessByteConsistent(Player.sensor_a) end
				gui.text(10, ty, "A: " .. str, COLOUR_SENSOR.A, COLOUR_BLACK)
				if Player.sensor_b == nil then str = "--" else str = ProcessByteConsistent(Player.sensor_b) end
				gui.text(40, ty, "B: " .. str, COLOUR_SENSOR.B, COLOUR_BLACK) ty = ty + spacing;
				if Player.sensor_c == nil then str = "--" else str = ProcessByteConsistent(Player.sensor_c) end
				gui.text(10, ty, "C: " .. str, COLOUR_SENSOR.C, COLOUR_BLACK)
				if Player.sensor_d == nil then str = "--" else str = ProcessByteConsistent(Player.sensor_d) end
				gui.text(40, ty, "D: " .. str, COLOUR_SENSOR.D, COLOUR_BLACK) ty = ty + spacing;
				if Player.sensor_e == nil then str = "--" else str = ProcessByteConsistent(Player.sensor_e) end
				gui.text(10, ty, "E: " .. str, COLOUR_SENSOR.E, COLOUR_BLACK)
				if Player.sensor_f == nil then str = "--" else str = ProcessByteConsistent(Player.sensor_f) end
				gui.text(40, ty, "F: " .. str, COLOUR_SENSOR.F, COLOUR_BLACK) ty = ty + spacing;
			end
		end
			
		
	---------------------
	--- Load all data ---
	---------------------
	-- This loads the data collected from code executions into the objects which are then drawn.
	-- We refresh the data AFTER drawing it because the overlay syncs with the game when the overlay is drawn one frame in the past
	
		if GamePaused == 0 and GameTimer ~= GameTimerPrevious then
			LoadCamera();
			LoadPlayer();
			LoadObjects();
		end
		GamePaused = memory.readword(OFF_PAUSED);
		
	----------------------
	--- Clear old data ---
	----------------------
		if  GameTimer ~= GameTimerPrevious then
			HitboxesTable = {};
			SensorsTable = {};
			SolidsTable = {};
			WalkingEdgesTable = {};
			SlopesTable = {};
			
			SENSORBUFFER.A = nil;
			SENSORBUFFER.B = nil;
			SENSORBUFFER.C = nil;
			SENSORBUFFER.D = nil;
			SENSORBUFFER.E = nil;
			SENSORBUFFER.F = nil;
		end
	----------------------------
	--- Draw Control Prompts ---
	----------------------------

		-- Process Controls
		if ControlGetState(ControlShowShortcuts) then	
			local ty = 0;

			for i, control in ipairs(OVERLAY_CONTROLS) do
				gui.text(320 - control.width - 40, ty, control.label, control.colour, COLOUR_BLACK);
				gui.text(320, ty, tostring(ControlGetState(i)), control.colour, COLOUR_BLACK);
				ty = ty + control.height;
			end
		else
			gui.text(320, 0, "Press " .. ControlGetShortcut(ControlShowShortcuts) .. " for Shortcuts", COLOUR_WHITE, COLOUR_BLACK);
		end
	end);

			