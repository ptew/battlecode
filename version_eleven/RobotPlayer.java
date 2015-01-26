package version_eleven;

import battlecode.common.*;

import java.util.*;
import java.lang.Math;

public class RobotPlayer {
	// Static Variables for the Match
	static RobotController rc;
	static Team my_team;
	static Team enemy_team;
	static int my_range;
	static Random rand;
	static Direction facing;
	static Direction[] directions = { Direction.NORTH, Direction.NORTH_EAST,
			Direction.EAST, Direction.SOUTH_EAST, Direction.SOUTH,
			Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST };
	static int strategy = 0;

	// BROADCAST CHANNELS
	static int X_RALLY_CHANNEL = 0;
	static int Y_RALLY_CHANNEL = 1;
	static int NUM_BEAVERS_CHANNEL = 2;
	static int NUM_ATTACKERS_CHANNEL = 3;
	static int NUM_SOLDIERS_CHANNEL = 4;
	static int NUM_BARRACKS_CHANNEL = 5;
	static int NUM_MINERFACTORY_CHANNEL = 6;
	static int NUM_MINERS_CHANNEL = 7;
	static int NUM_TANKFACTORY_CHANNEL = 8;
	static int NUM_HELIPAD_CHANNEL = 9;
	static int X_ATTACK_CHANNEL = 10;
	static int Y_ATTACK_CHANNEL = 11;
	static int STRATEGY_CHANNEL = 12;
	static int X_ISOLATED_CHANNEL = 13;
	static int Y_ISOLATED_CHANNEL = 14;
	static int NUM_TECH_CHANNEL = 15;
	static int NUM_TRAINING_FIELD_CHANNEL = 16;
	static int NUM_COMMANDERS_CHANNEL = 17;
	static int COMMANDER_ID_CHANNEL = 18;
	static int NUM_ISOLATED_TOWER_CHANNEL = 100;
	static int ISOLATED_TOWER_ATTACK_CHANNEL = 101;

	// LOGIC CONSTANTS
	static int MINERFACTORY_LIMIT = 1;
	static int BARRACKS_LIMIT = 4;
	static int MINER_LIMIT = 12;
	static int BEAVER_LIMIT = 3;
	static int TANKFACTORY_LIMIT = 3;
	static int FINAL_PUSH_ROUND = 1700;
	static int PATHFINDING_ROUND_LIMIT = 10;
	static int TOWER_THREAT_DISTANCE = 50;
	static int DEFENSE_STRATEGY = 0;
	static int DRONES_STRATEGY = 1;
	static int SOLDIERS_STRATEGY = 2;
	static int HQ_BOX = 11;
	static int HQ_DENSITY_LIMIT = 30;
	static int ATTACK_SQUAD_SIZE = 10;
	static int ATTACK_SQUAD_RADIUS = 10;
	static int LARGE_ATTACK_SQUAD_SIZE = 40;
	static int LARGE_ATTACK_SQUAD_RADIUS = 30;
	static int TOWER_ISOLATION_FACTOR = (int) 1.5
			* RobotType.TOWER.attackRadiusSquared;
	static int BUILD_ORDER = 0;
	static int ATTACK_SEQUENCE_ORDER = 1;
	static int ATTACK_ISOLATED_ORDER = 3;
	static int COMMANDER_RALLY_ORDER = 4;
	static int ISO_TOWER_LIMIT = 2;
	static int TECH_LIMIT = 1;
	static int TRAINING_FIELD_LIMIT = 1;
	static int ISO_TARGET_UPDATE_LIMIT = 150;
	static int LOITER_ROUND_LIMIT = 10;
	static int LOITER_DISTANCE = 10;

	public static void run(RobotController controller) {
		rc = controller;
		rand = new Random(rc.getID());

		my_range = rc.getType().attackRadiusSquared;
		my_team = rc.getTeam();
		enemy_team = my_team.opponent();
		facing = get_random_direction();// randomize starting direction
		BaseBot bot;

		switch (rc.getType()) {
		case HQ:
			bot = new HQ(rc);
			try {
				((HQ) bot).generate_attack_sequence((HQ) bot);
			} catch (GameActionException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			break;
		case TOWER:
			bot = new Tower(rc);
			break;
		case SOLDIER:
			bot = new SimpleAttacker(rc);
			break;
		case BEAVER:
			bot = new Beaver(rc);
			break;
		case BARRACKS:
			bot = new SimpleBuilder(rc);
			break;
		case MINER:
			bot = new Miner(rc);
			break;
		case MINERFACTORY:
			bot = new SimpleBuilder(rc);
			break;
		case TANK:
			bot = new SimpleAttacker(rc);
			break;
		case TANKFACTORY:
			bot = new SimpleBuilder(rc);
			break;
		case DRONE:
			bot = new SimpleAttacker(rc);
			break;
		case HELIPAD:
			bot = new SimpleBuilder(rc);
			break;
		case TECHNOLOGYINSTITUTE:
			bot = new SimpleBuilder(rc);
			break;
		case TRAININGFIELD:
			bot = new SimpleBuilder(rc);
			break;
		case COMMANDER:
			bot = new Commander(rc);
			break;
		default:
			bot = new BaseBot(rc);
			break;
		}

		while (true) {
			try {
				bot.go();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static class BaseBot {
		protected RobotController rc;
		protected MapLocation myHQ, theirHQ;
		protected Team myTeam, theirTeam;
		protected Queue<MapLocation> path;

		public BaseBot(RobotController rc) {
			this.rc = rc;
			// TODO: Have the HQ do this and broadcast it
			this.myHQ = rc.senseHQLocation();
			this.theirHQ = rc.senseEnemyHQLocation();
			this.myTeam = rc.getTeam();
			this.theirTeam = this.myTeam.opponent();
			this.path = new LinkedList<MapLocation>();
		}

		/*
		 * 
		 * BASEBOT HELPER FUNCTIONS
		 */

		public Direction[] getDirectionsToward(MapLocation dest) {
			Direction toDest = rc.getLocation().directionTo(dest);
			Direction[] dirs = { toDest, toDest.rotateLeft(),
					toDest.rotateRight(), toDest.rotateLeft().rotateLeft(),
					toDest.rotateRight().rotateRight() };

			return dirs;
		}

		public Direction getMoveDir(MapLocation dest) {
			Direction[] dirs = getDirectionsToward(dest);
			for (Direction d : dirs) {
				if (rc.canMove(d)) {
					return d;
				}
			}
			return null;
		}

		public Queue<MapLocation> move_to_location(BaseBot bot, MapLocation loc)
				throws GameActionException {
			Direction dir = facing;
			if (rand.nextDouble() < 0.2) {
				dir = get_random_direction();
			} else {
				dir = getMoveDir(loc);
				if (dir == null) {
					dir = facing;
				}
			}

			MapLocation tileInFront = rc.getLocation().add(dir);

			boolean tileInFrontSafe = true;
			MapLocation[] enemyTowers = rc.senseEnemyTowerLocations();
			for (MapLocation m : enemyTowers) {
				if (!m.equals(loc)
						&& m.distanceSquaredTo(tileInFront) <= RobotType.TOWER.attackRadiusSquared) {
					tileInFrontSafe = false;
					break;
				}
			}

			// check that we are not facing off the edge of the map
			if (rc.senseTerrainTile(tileInFront) != TerrainTile.NORMAL
					&& rc.getType() != RobotType.DRONE || !tileInFrontSafe) {
				dir = dir.rotateLeft();
			} else {
				// try to move in the facing direction
				if (rc.isCoreReady() && rc.canMove(dir)) {
					rc.move(dir);
				}
			}

			return bot.path;
		}

		public Direction get_spawn_direction(RobotType type) {
			Direction[] dirs = getDirectionsToward(this.theirHQ);
			for (Direction d : dirs) {
				if (rc.canSpawn(d, type)) {
					return d;
				}
			}
			return null;
		}

		public Direction get_build_direction(RobotType type) {
			Direction[] dirs = getDirectionsToward(this.theirHQ);
			for (Direction d : dirs) {
				if (rc.canBuild(d, type)) {
					return d;
				}
			}
			return null;
		}

		public RobotInfo[] getAllies() {
			RobotInfo[] allies = rc
					.senseNearbyRobots(Integer.MAX_VALUE, myTeam);
			return allies;
		}

		public RobotInfo[] getEnemiesInAttackingRange() {
			RobotInfo[] enemies = rc.senseNearbyRobots(
					rc.getType().attackRadiusSquared, theirTeam);
			return enemies;
		}

		public void attackLeastHealthEnemy(RobotInfo[] enemies)
				throws GameActionException {
			if (enemies.length == 0) {
				return;
			}

			double minEnergon = Double.MAX_VALUE;
			MapLocation toAttack = null;
			for (RobotInfo info : enemies) {
				if (info.health < minEnergon) {
					toAttack = info.location;
					minEnergon = info.health;
				}
			}

			rc.attackLocation(toAttack);
		}

		public void beginningOfTurn() {
			if (rc.senseEnemyHQLocation() != null) {
				this.theirHQ = rc.senseEnemyHQLocation();
			}
		}

		public void endOfTurn() {
		}

		public void go() throws GameActionException {
			beginningOfTurn();
			transfer_supplies();
			execute();
			endOfTurn();
		}

		public void execute() throws GameActionException {
			rc.yield();
		}

	}

	/*
	 * 
	 * HQ LOGIC
	 */
	public static class HQ extends BaseBot {
		public int attack_counter;
		public int isolated_counter;
		private MapLocation[] enemy_towers;
		public static List<MapLocation> isolated_targets;
		static List<MapLocation> attack_sequence;
		public int prev_number_of_towers;

		public static int xMin, xMax, yMin, yMax;
		public static int xpos, ypos;
		public static int totalNormal, totalVoid, totalProcessed;
		public static int towerThreat;

		public static int hq_x_min, hq_y_min, hq_x_max, hq_y_max;
		public static int hq_x, hq_y;
		public static int hq_void;

		public static double ratio;
		public static int hq_density;
		public static boolean isFinished;

		public HQ(RobotController rc) {
			super(rc);
			attack_counter = 0;
			isolated_counter = 0;
			isolated_targets = new ArrayList<MapLocation>();
			attack_sequence = new ArrayList<MapLocation>();
			enemy_towers = rc.senseEnemyTowerLocations();
			prev_number_of_towers = enemy_towers.length;

			xMin = Math.min(this.myHQ.x, this.theirHQ.x);
			xMax = Math.max(this.myHQ.x, this.theirHQ.x);
			yMin = Math.min(this.myHQ.y, this.theirHQ.y);
			yMax = Math.max(this.myHQ.y, this.theirHQ.y);

			xpos = xMin;
			ypos = yMin;

			hq_x_min = this.myHQ.x - HQ_BOX;
			hq_y_min = this.myHQ.y - HQ_BOX;
			hq_x_max = this.myHQ.x + HQ_BOX;
			hq_y_max = this.myHQ.y + HQ_BOX;

			hq_x = hq_x_min;
			hq_y = hq_y_min;

			hq_void = 0;

			totalNormal = totalVoid = totalProcessed = 0;
			towerThreat = 0;
			strategy = 0;
			isFinished = false;
		}

		public void analyze_HQ_box() {
			while (hq_y < hq_y_max + 1) {
				TerrainTile t = rc
						.senseTerrainTile(new MapLocation(xpos, ypos));

				if (t == TerrainTile.VOID) {
					hq_void++;
				}
				hq_x++;
				if (hq_x == hq_x_max + 1) {
					hq_x = hq_x_min;
					hq_y++;
				}

				if (Clock.getBytecodesLeft() < 100) {
					return;
				}
			}
		}

		public void analyze_map() {
			while (ypos < yMax + 1) {
				TerrainTile t = rc
						.senseTerrainTile(new MapLocation(xpos, ypos));

				if (t == TerrainTile.NORMAL) {
					totalNormal++;
					totalProcessed++;
				} else if (t == TerrainTile.VOID) {
					totalVoid++;
					totalProcessed++;
				}
				xpos++;
				if (xpos == xMax + 1) {
					xpos = xMin;
					ypos++;
				}

				if (Clock.getBytecodesLeft() < 100) {
					return;
				}
			}
			ratio = (double) totalNormal / totalProcessed;
		}

		public void analyze_towers() {
			MapLocation[] towers = enemy_towers;
			towerThreat = 0;

			for (int i = 0; i < towers.length; ++i) {
				MapLocation towerLoc = towers[i];

				if ((xMin <= towerLoc.x && towerLoc.x <= xMax
						&& yMin <= towerLoc.y && towerLoc.y <= yMax)
						|| towerLoc.distanceSquaredTo(this.theirHQ) <= TOWER_THREAT_DISTANCE) {
					for (int j = 0; j < towers.length; ++j) {
						if (towers[j].distanceSquaredTo(towerLoc) <= TOWER_THREAT_DISTANCE) {
							towerThreat++;
						}
					}
				}
			}

			isFinished = true;
		}

		public void choose_strategy() throws GameActionException {
			if (hq_void > HQ_DENSITY_LIMIT) {
				strategy = DRONES_STRATEGY;
			} else if (towerThreat >= 10) {
				// play defensive
				strategy = DEFENSE_STRATEGY;
			} else {
				if (ratio <= 0.85) {
					// build drones
					strategy = DRONES_STRATEGY;
				} else {
					// build soldiers
					strategy = SOLDIERS_STRATEGY;
				}
			}
			strategy = DEFENSE_STRATEGY;
			rc.broadcast(STRATEGY_CHANNEL, strategy);
		}

		private void generate_attack_sequence(HQ bot)
				throws GameActionException {
			List<MapLocation> enemy_locations = new ArrayList<MapLocation>();
			for (MapLocation t : bot.enemy_towers) {
				enemy_locations.add(t);
			}
			enemy_locations.add(bot.theirHQ);

			// Check for isolated towers and add closet ones first
			Boolean isolated;
			List<MapLocation> temp_iso = new ArrayList<MapLocation>();
			for (int i = 0; i < enemy_locations.size(); ++i) {
				isolated = true;
				for (int j = 0; j < enemy_locations.size(); ++j) {
					if (enemy_locations.get(i).distanceSquaredTo(
							enemy_locations.get(j)) <= TOWER_ISOLATION_FACTOR
							&& i != j) {
						isolated = false;
					}
				}
				if (isolated) {
					temp_iso.add(enemy_locations.get(i));
				}
			}

			int iso_target_count = temp_iso.size();
			int max_distance;
			int dist_to_hq;
			MapLocation max = null;
			if (iso_target_count > 0) {
				rc.broadcast(ISOLATED_TOWER_ATTACK_CHANNEL, 0);

				// Remove the isolated towers from enemy_locations so they are
				// not
				// considered later. Also, Sort by proximity to HQ
				for (int i = 0; i < ISO_TOWER_LIMIT; i++) {
					max = null;
					max_distance = Integer.MIN_VALUE;
					for (MapLocation loc : temp_iso) {
						dist_to_hq = loc.distanceSquaredTo(bot.theirHQ);
						if (dist_to_hq > max_distance
								&& !isolated_targets.contains(loc)) {
							max = loc;
							max_distance = dist_to_hq;
						}
					}

					if (max != null) {
						enemy_locations.remove(max);
						isolated_targets.add(max);
					}
				}
			}

			// Add the remaining targets by closeness to previous target
			MapLocation last_location = bot.myHQ;
			int shortest_distance = Integer.MAX_VALUE;
			MapLocation closest;
			while (enemy_locations.isEmpty() != true) {
				closest = enemy_locations.get(0);
				for (MapLocation loc : enemy_locations) {
					int new_distance = last_location.distanceSquaredTo(loc) + 2
							* bot.myHQ.distanceSquaredTo(loc);
					if (new_distance < shortest_distance) {
						shortest_distance = new_distance;
						closest = loc;
					}
				}
				last_location = closest;
				attack_sequence.add(closest);
				enemy_locations.remove(closest);
				shortest_distance = Integer.MAX_VALUE;
			}

			rc.broadcast(X_ATTACK_CHANNEL,
					attack_sequence.get(attack_counter).x);
			rc.broadcast(Y_ATTACK_CHANNEL,
					attack_sequence.get(attack_counter).y);
		}

		private MapLocation closest_tower(MapLocation hq, MapLocation target) {
			MapLocation[] towers = rc.senseTowerLocations();
			int shortest_distance = Integer.MAX_VALUE;
			MapLocation closest = hq;
			for (MapLocation loc : towers) {
				int new_distance = target.distanceSquaredTo(loc);
				if (new_distance < shortest_distance) {
					shortest_distance = new_distance;
					closest = loc;
				}
			}
			return closest;
		}

		private MapLocation generate_rally_point(HQ bot)
				throws GameActionException {
			if (isolated_targets.size() > 0) {
				return closest_tower(bot.myHQ, isolated_targets.get(0));
			} else if (attack_sequence.size() == 0) {
				return halfway_between(bot.myHQ, bot.theirHQ);
			}
			return halfway_between(bot.myHQ,
					attack_sequence.get(bot.attack_counter));
		}

		public void execute() throws GameActionException {
			// Spawn beavers to get the party going
			int numBeavers = rc.readBroadcast(NUM_BEAVERS_CHANNEL);
			if (rc.isCoreReady() && rc.getTeamOre() > 100
					&& numBeavers < BEAVER_LIMIT) {
				Direction newDir = get_spawn_direction(RobotType.BEAVER);
				if (newDir != null) {
					rc.spawn(newDir, RobotType.BEAVER);
					rc.broadcast(NUM_BEAVERS_CHANNEL, numBeavers + 1);
				}
			}

			// Attack Enemies
			RobotInfo[] enemies = getEnemiesInAttackingRange();
			if (enemies.length > 0) {
				// attack!
				if (rc.isWeaponReady()) {
					attackLeastHealthEnemy(enemies);
				}
			}

			// Detect any changes in the current target sequence (isolated or
			// attack)
			enemy_towers = rc.senseEnemyTowerLocations();
			if (enemy_towers.length < prev_number_of_towers) {
				rc.broadcast(
						id_to_channel(rc.readBroadcast(COMMANDER_ID_CHANNEL)),
						COMMANDER_RALLY_ORDER);
				prev_number_of_towers = enemy_towers.length;

				Boolean missing;
				MapLocation to_remove = null;
				for (MapLocation iso : isolated_targets) {
					missing = true;
					for (MapLocation tower : enemy_towers) {
						if (iso.equals(tower)) {
							missing = false;
							break;
						}
					}
					if (missing) {
						to_remove = iso;
						break;
					}
				}
				if (to_remove == null) {
					attack_counter++;
				} else {
					isolated_targets.remove(to_remove);
				}
			}

			// Set the current rally point for the next flock of troops
			MapLocation rally_point = generate_rally_point(this);
			rc.broadcast(X_RALLY_CHANNEL, rally_point.x);
			rc.broadcast(Y_RALLY_CHANNEL, rally_point.y);

			// Set the current attack position for any attacking troops
			if (isolated_targets.size() > 0) {
				rc.broadcast(X_ISOLATED_CHANNEL, isolated_targets.get(0).x);
				rc.broadcast(Y_ISOLATED_CHANNEL, isolated_targets.get(0).y);
			} else {
				rc.broadcast(X_ISOLATED_CHANNEL,
						attack_sequence.get(attack_counter).x);
				rc.broadcast(Y_ISOLATED_CHANNEL,
						attack_sequence.get(attack_counter).y);
				rc.broadcast(X_ATTACK_CHANNEL,
						attack_sequence.get(attack_counter).x);
				rc.broadcast(Y_ATTACK_CHANNEL,
						attack_sequence.get(attack_counter).y);
			}

			// Send the troops at the current rally_point if there are enough
			RobotInfo[] rallied_troops;
			if (isolated_targets.size() > 0) {
				rallied_troops = rc.senseNearbyRobots(rally_point,
						ATTACK_SQUAD_RADIUS, this.myTeam);
				if (rallied_troops.length > ATTACK_SQUAD_SIZE) {
					for (RobotInfo bot : rallied_troops) {
						rc.broadcast(id_to_channel(bot.ID),
								ATTACK_ISOLATED_ORDER);
					}
				}
			} else {
				rallied_troops = rc.senseNearbyRobots(rally_point,
						LARGE_ATTACK_SQUAD_RADIUS, this.myTeam);
				if (rallied_troops.length > LARGE_ATTACK_SQUAD_SIZE) {
					for (RobotInfo bot : rallied_troops) {
						rc.broadcast(id_to_channel(bot.ID),
								ATTACK_SEQUENCE_ORDER);
					}
				}
			}

			// Perform board analysis
			if (!isFinished) {
				analyze_HQ_box();
				if (hq_void < HQ_DENSITY_LIMIT) {
					analyze_map();
				}
				analyze_towers();
			} else {
				choose_strategy();
			}

			rc.yield();
		}
	}

	/*
	 * 
	 * BEAVER LOGIC
	 */
	public static class Beaver extends BaseBot {
		public Beaver(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			RobotInfo[] enemies = getEnemiesInAttackingRange();

			if (enemies.length > 0) {
				// attack!
				if (rc.isWeaponReady()) {
					attackLeastHealthEnemy(enemies);
				}
			}
			int round_number = Clock.getRoundNum();
			if (rc.readBroadcast(NUM_TECH_CHANNEL) < TECH_LIMIT
					&& rc.getTeamOre() >= RobotType.TECHNOLOGYINSTITUTE.oreCost
					&& round_number < 300) {

				build_unit(RobotType.TECHNOLOGYINSTITUTE,
						get_random_direction(), NUM_TECH_CHANNEL);

			} else if (rc.readBroadcast(NUM_TRAINING_FIELD_CHANNEL) < TRAINING_FIELD_LIMIT
					&& rc.getTeamOre() >= RobotType.TRAININGFIELD.oreCost
					&& round_number < 400) {

				build_unit(RobotType.TRAININGFIELD, get_random_direction(),
						NUM_TRAINING_FIELD_CHANNEL);

			} else if (Clock.getRoundNum() < 300
					&& rc.readBroadcast(NUM_MINERFACTORY_CHANNEL) < MINERFACTORY_LIMIT) {

				build_unit(RobotType.MINERFACTORY, get_random_direction(),
						NUM_MINERFACTORY_CHANNEL);

			} else {
				int strategy = rc.readBroadcast(STRATEGY_CHANNEL);

				RobotType to_build = RobotType.BARRACKS;
				int channel = NUM_BARRACKS_CHANNEL;
				if (strategy == 1) {
					to_build = RobotType.HELIPAD;
					channel = NUM_HELIPAD_CHANNEL;
				} else if (strategy == 0) {
					if (rc.checkDependencyProgress(RobotType.BARRACKS) == DependencyProgress.DONE) {
						to_build = RobotType.TANKFACTORY;
						channel = NUM_TANKFACTORY_CHANNEL;
					}
				}

				build_unit(to_build, get_build_direction(to_build), channel);
			}

			mine_and_move();

			rc.yield();
		}
	}

	/*
	 * 
	 * COMMANDER LOGIC
	 */
	public static class Commander extends SimpleAttacker {

		public Commander(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			RobotInfo[] enemies = getEnemiesInAttackingRange();
			rc.broadcast(COMMANDER_ID_CHANNEL, rc.getID());
			if (enemies.length > 0) {
				// attack!
				if (rc.isWeaponReady()) {
					attackLeastHealthEnemy(enemies);
				}
			} else {
				int id = rc.getID();
				MapLocation next_loc = get_next_move(this, id);
				this.path = move_to_location(this, next_loc);
			}
			rc.yield();
		}

		private MapLocation get_next_move(BaseBot bot, int id)
				throws GameActionException {
			MapLocation loc;
			MapLocation attack_loc = location_from_channel(X_ATTACK_CHANNEL,
					Y_ATTACK_CHANNEL);
			if (Clock.getRoundNum() > FINAL_PUSH_ROUND) {
				loc = attack_loc;
			} else {
				int order = rc.readBroadcast(id_to_channel(id));

				if (order == ATTACK_SEQUENCE_ORDER && !low_attack_density(bot)) {
					loc = attack_loc;
				} else {
					loc = halfway_between(bot.myHQ, attack_loc);
				}
			}
			// set_robot_string(rc, loc.toString());
			return loc;
		}

		public Queue<MapLocation> move_to_location(SimpleAttacker bot,
				MapLocation loc) throws GameActionException {
			MapLocation current_location = rc.getLocation();

			if (bot.path == null || bot.path.size() == 0
					|| !loc.equals(last_dest)) {
				last_dest = loc;
				MapLocation finish = closer_finish(current_location, loc);
				// MapLocation finish = loc;
				if (frontier.size() == 0 || cost_so_far.size() == 0) {
					frontier = new PriorityQueue<Tuple<MapLocation, Integer>>(
							100, comparater);
					came_from = new Hashtable<MapLocation, MapLocation>();
					cost_so_far = new Hashtable<MapLocation, Integer>();
					frontier.add(new Tuple<MapLocation, Integer>(
							current_location, 0));
					cost_so_far.put(current_location, 0);
					came_from.put(current_location, current_location);
				}
				bot.path = a_star_search(current_location, finish, frontier,
						came_from, cost_so_far);
				if (bot.path != null && bot.path.size() > 0) {
					set_robot_string(rc, bot.path.peek().toString());
				} else {
					set_robot_string(rc, Integer.toString(frontier.size()));
				}
			}

			// try to move in the facing direction
			if (rc.isCoreReady() && bot.path != null && bot.path.size() > 0) {
				MapLocation dest = bot.path.peek();
				Direction dir = current_location.directionTo(dest);
				if (rc.canMove(dir)) {
					rc.move(dir);
					bot.path.remove();
				} else {
					dir = getMoveDir(dest);
					if (dir != null) {
						rc.move(dir);
						bot.path.remove();
					}
				}

			}

			return bot.path;
		}
	}

	/*
	 * 
	 * SIMPLEATTACKER LOGIC
	 */
	public static class SimpleAttacker extends BaseBot {
		private MapLocation existing_isolated_target;
		protected MapLocation last_dest;
		private int last_iso_target_update;
		private int rounds_close_to_dest;
		PriorityQueue<Tuple<MapLocation, Integer>> frontier;
		Map<MapLocation, MapLocation> came_from;
		Map<MapLocation, Integer> cost_so_far;

		public SimpleAttacker(RobotController rc) {
			super(rc);
			existing_isolated_target = null;
			rounds_close_to_dest = 0;
			last_dest = null;
			frontier = new PriorityQueue<Tuple<MapLocation, Integer>>(100,
					comparater);
			came_from = new Hashtable<MapLocation, MapLocation>();
			cost_so_far = new Hashtable<MapLocation, Integer>();
		}

		public void execute() throws GameActionException {
			RobotInfo[] enemies = getEnemiesInAttackingRange();

			if (enemies.length > 0) {
				// attack!
				if (rc.isWeaponReady()) {
					attackLeastHealthEnemy(enemies);
				}
			} else if (rc.isCoreReady()) {
				int id = rc.getID();
				MapLocation dest = get_next_move(this, id);
				if (!dest.equals(last_dest)) {
					last_dest = dest;
					rounds_close_to_dest = 0;
				} else if (rc.getLocation().distanceSquaredTo(dest) < LOITER_DISTANCE) {
					rounds_close_to_dest++;
				}

				if (rounds_close_to_dest < LOITER_ROUND_LIMIT) {
					this.path = move_to_location(this, dest);
				}
			}
			rc.yield();
		}

		// public void execute() throws GameActionException {
		// RobotInfo[] enemies = getEnemiesInAttackingRange();
		// if (enemies.length > 0) {
		// // attack!
		// if (rc.isWeaponReady()) {
		// attackLeastHealthEnemy(enemies);
		// }
		// } else {
		// int id = rc.getID();
		// MapLocation next_loc = get_next_move(this, id);
		// this.path = move_to_location(this, next_loc);
		// }
		// rc.yield();
		// }

		private MapLocation get_next_move(BaseBot bot, int id)
				throws GameActionException {
			int x_channel, y_channel;

			if (Clock.getRoundNum() > FINAL_PUSH_ROUND) {
				x_channel = X_ATTACK_CHANNEL;
				y_channel = Y_ATTACK_CHANNEL;

			} else {
				int order = rc.readBroadcast(id_to_channel(id));
				if (order == ATTACK_ISOLATED_ORDER) {
					if (existing_isolated_target == null
							|| Clock.getRoundNum() - last_iso_target_update > ISO_TARGET_UPDATE_LIMIT) {
						existing_isolated_target = location_from_channel(
								X_ISOLATED_CHANNEL, Y_ISOLATED_CHANNEL);
						last_iso_target_update = Clock.getRoundNum();
					}
					return existing_isolated_target;
				} else if (order == ATTACK_SEQUENCE_ORDER
						&& !low_attack_density(bot)) {
					x_channel = X_ATTACK_CHANNEL;
					y_channel = Y_ATTACK_CHANNEL;
				} else {
					// go to build rally point
					x_channel = X_RALLY_CHANNEL;
					y_channel = Y_RALLY_CHANNEL;
				}
			}
			return location_from_channel(x_channel, y_channel);
		}

		// public Queue<MapLocation> move_to_location(SimpleAttacker bot,
		// MapLocation loc) throws GameActionException {
		// MapLocation current_location = rc.getLocation();
		//
		// if (bot.path == null || bot.path.size() == 0
		// || !loc.equals(last_dest)) {
		// last_dest = loc;
		// MapLocation finish = closer_finish(current_location, loc);
		// // MapLocation finish = loc;
		// if (frontier.size() == 0 || cost_so_far.size() == 0) {
		// frontier = new PriorityQueue<Tuple<MapLocation, Integer>>(
		// 100, comparater);
		// came_from = new Hashtable<MapLocation, MapLocation>();
		// cost_so_far = new Hashtable<MapLocation, Integer>();
		// frontier.add(new Tuple<MapLocation, Integer>(
		// current_location, 0));
		// cost_so_far.put(current_location, 0);
		// came_from.put(current_location, current_location);
		// }
		// bot.path = a_star_search(current_location, finish, frontier,
		// came_from, cost_so_far);
		// if (bot.path != null && bot.path.size() > 0) {
		// set_robot_string(rc, bot.path.peek().toString());
		// } else {
		// set_robot_string(rc, Integer.toString(frontier.size()));
		// }
		// }
		//
		// // try to move in the facing direction
		// if (rc.isCoreReady() && bot.path != null && bot.path.size() > 0) {
		// MapLocation dest = bot.path.peek();
		// Direction dir = current_location.directionTo(dest);
		// if (rc.canMove(dir)) {
		// rc.move(dir);
		// bot.path.remove();
		// } else {
		// dir = getMoveDir(dest);
		// if(dir != null){
		// rc.move(dir);
		// bot.path.remove();
		// }
		// }
		//
		// }
		//
		// return bot.path;
		// }
		public Queue<MapLocation> move_to_location(BaseBot bot, MapLocation loc)
				throws GameActionException {
			Direction dir = facing;
			if (rand.nextDouble() < 0.2) {
				dir = get_random_direction();
			} else {
				dir = getMoveDir(loc);
				if (dir == null) {
					dir = facing;
				}
			}

			MapLocation tileInFront = rc.getLocation().add(dir);

			boolean tileInFrontSafe = true;
			MapLocation[] enemyTowers = rc.senseEnemyTowerLocations();
			for (MapLocation m : enemyTowers) {
				if (!m.equals(loc)
						&& m.distanceSquaredTo(tileInFront) <= RobotType.TOWER.attackRadiusSquared) {
					tileInFrontSafe = false;
					break;
				}
			}

			// check that we are not facing off the edge of the map
			if (rc.senseTerrainTile(tileInFront) != TerrainTile.NORMAL
					&& rc.getType() != RobotType.DRONE || !tileInFrontSafe) {
				dir = dir.rotateLeft();
			} else {
				// try to move in the facing direction
				if (rc.isCoreReady() && rc.canMove(dir)) {
					rc.move(dir);
				}
			}

			return bot.path;
		}
	}

	/*
	 * 
	 * SIMPLEBUILDER LOGIC
	 */
	public static class SimpleBuilder extends BaseBot {
		public SimpleBuilder(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			RobotType spawn_type = null;
			Boolean limit = true;
			int channel = -1;
			switch (rc.getType()) {
			case MINERFACTORY:
				spawn_type = RobotType.MINER;
				limit = rc.readBroadcast(NUM_MINERS_CHANNEL) < MINER_LIMIT;
				channel = NUM_MINERS_CHANNEL;
				break;
			case BARRACKS:
				spawn_type = RobotType.MINER;
				break;
			case TANKFACTORY:
				spawn_type = RobotType.TANK;
				break;
			case HELIPAD:
				spawn_type = RobotType.DRONE;
				break;
			case TRAININGFIELD:
				spawn_type = RobotType.COMMANDER;
				channel = NUM_COMMANDERS_CHANNEL;
				break;
			default:
				break;
			}

			if (spawn_type != null && rc.getTeamOre() > spawn_type.oreCost
					&& limit) {
				if (channel > 0) {
					spawn_unit(spawn_type, get_spawn_direction(spawn_type),
							channel);
				} else {
					spawn_unit(spawn_type, get_spawn_direction(spawn_type));
				}
			}

			rc.yield();
		}
	}

	/*
	 * 
	 * MINER LOGIC
	 */
	public static class Miner extends BaseBot {
		public Miner(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			RobotInfo[] enemies = getEnemiesInAttackingRange();

			if (enemies.length > 0) {
				// attack!
				if (rc.isWeaponReady()) {
					attackLeastHealthEnemy(enemies);
				}
			}

			circular_mine_move();
			rc.yield();
		}

		// if north, try to move left, then right, then northwest,
		// northeast,
		// north else movearound
		// if south try to move west, east, southwest, southeast, south else
		// movearound
		// if west, try to move south, north, southwest, northwest, west
		// else
		// movearound
		// if east, try to move north, south, northeast, southeast, east
		// else
		// movearound

		static Direction[] NORTH_MINE_DIRS = { Direction.WEST, Direction.EAST,
				Direction.NORTH_WEST, Direction.NORTH_EAST, Direction.NORTH };
		static Direction[] SOUTH_MINE_DIRS = { Direction.EAST, Direction.WEST,
				Direction.SOUTH_EAST, Direction.SOUTH_WEST, Direction.SOUTH };
		static Direction[] WEST_MINE_DIRS = { Direction.SOUTH, Direction.NORTH,
				Direction.SOUTH_WEST, Direction.NORTH_WEST, Direction.WEST };
		static Direction[] EAST_MINE_DIRS = { Direction.NORTH, Direction.SOUTH,
				Direction.NORTH_EAST, Direction.SOUTH_EAST, Direction.EAST };

		private void circular_mine_move() throws GameActionException {
			// Find where the head quarters is.
			if (rc.isCoreReady()) {

				if (rc.senseOre(rc.getLocation()) > 1) {// there is ore, so try
														// to
														// mine
					if (rc.canMine()) {
						rc.mine();
					}
				} else {
					MapLocation current_location = rc.getLocation();
					Direction[] dir_list = get_direction_list(determine_dir_from_hq(
							this.myHQ, current_location));

					Direction max_move_dir = null;
					double max_ore = Integer.MIN_VALUE;
					double ore;
					MapLocation[] enemyTowers = rc.senseEnemyTowerLocations();
					boolean tileInFrontSafe;
					for (Direction dir : dir_list) {
						tileInFrontSafe = true;
						MapLocation tileInFront = current_location.add(dir);

						if (rc.senseOre(tileInFront) > 2) {
							// check that the direction in front is not a tile
							// that
							// can
							// be
							// attacked
							// by the enemy towers
							for (MapLocation m : enemyTowers) {
								if (m.distanceSquaredTo(tileInFront) <= RobotType.TOWER.attackRadiusSquared) {
									tileInFrontSafe = false;
									break;
								}
							}

							// check that we are not facing off the edge of the
							// map
							if (rc.senseTerrainTile(tileInFront) == TerrainTile.NORMAL
									&& tileInFrontSafe) {
								ore = rc.senseOre(tileInFront);
								// try to move in the facing direction
								if (rc.canMove(dir) && ore > max_ore) {
									max_move_dir = dir;
									max_ore = ore;
								}
							}
						}

					}

					if (max_move_dir == null) {
						move_around();
					} else {
						if (rc.isCoreReady()) {
							rc.move(max_move_dir);
						}
					}
				}
			}
		}

		private Direction determine_dir_from_hq(MapLocation hq,
				MapLocation current_loc) {
			int x_diff = current_loc.x - hq.x;
			int y_diff = current_loc.y - hq.y;
			boolean mag_x_larger = Math.abs(x_diff) > Math.abs(y_diff);

			// positive x_diff ==> to the east
			// positive y_diff ==> to the south
			Direction dir;

			if (x_diff > 0 && y_diff > 0) {
				if (mag_x_larger) {
					dir = Direction.EAST;
				} else {
					dir = Direction.SOUTH;
				}
			} else if (x_diff > 0 && y_diff < 0) {
				if (mag_x_larger) {
					dir = Direction.EAST;
				} else {
					dir = Direction.NORTH;
				}
			} else if (x_diff < 0 && y_diff > 0) {
				if (mag_x_larger) {
					dir = Direction.WEST;
				} else {
					dir = Direction.SOUTH;
				}
			} else {
				if (mag_x_larger) {
					dir = Direction.WEST;
				} else {
					dir = Direction.NORTH;
				}
			}
			return dir;
		}

		private static Direction[] get_direction_list(Direction dir) {
			Direction[] dir_list;
			if (dir.equals(Direction.NORTH)) {
				dir_list = NORTH_MINE_DIRS;
			} else if (dir.equals(Direction.SOUTH)) {
				dir_list = SOUTH_MINE_DIRS;
			} else if (dir.equals(Direction.WEST)) {
				dir_list = WEST_MINE_DIRS;
			} else {
				dir_list = EAST_MINE_DIRS;
			}
			return dir_list;
		}
	}

	/*
	 * 
	 * TOWER LOGIC
	 */
	public static class Tower extends BaseBot {
		public Tower(RobotController rc) {
			super(rc);
		}

		public void execute() throws GameActionException {
			try {
				RobotInfo[] enemies = getEnemiesInAttackingRange();

				if (enemies.length > 0) {
					// attack!
					if (rc.isWeaponReady()) {
						attackLeastHealthEnemy(enemies);
					}
				}
			} catch (Exception e) {
				System.out.println("Tower Exception");
				e.printStackTrace();
			}

			rc.yield();
		}
	}

	/*
	 * 
	 * HELPER FUNCTIONS
	 */
	private static void transfer_supplies() throws GameActionException {
		RobotInfo[] nearbyAllies = rc.senseNearbyRobots(rc.getLocation(),
				GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED, rc.getTeam());
		double lowestSupply = rc.getSupplyLevel();
		double transferAmount = 0;
		MapLocation suppliesToThisLocation = null;
		for (RobotInfo ri : nearbyAllies) {
			if (ri.supplyLevel < lowestSupply) {
				lowestSupply = ri.supplyLevel;
				transferAmount = (rc.getSupplyLevel() - ri.supplyLevel) / 2;
				suppliesToThisLocation = ri.location;
			}
		}
		if (suppliesToThisLocation != null) {
			rc.transferSupplies((int) transferAmount, suppliesToThisLocation);
		}
	}

	// This method will attack an enemy in sight, if there is one
	static void attack_something() throws GameActionException {
		RobotInfo[] enemies = rc.senseNearbyRobots(my_range, enemy_team);
		if (enemies.length > 0) {
			rc.attackLocation(enemies[0].location);
		}
	}

	static void set_robot_string(RobotController rc, String str) {
		try {
			rc.setIndicatorString(0, str);
		} catch (Exception e) {
			System.out.println("Unexpected exception");
			e.printStackTrace();
		}
	}

	private static Direction get_random_direction() {
		return Direction.values()[(int) (rand.nextDouble() * 8)];
	}

	private static void mine_and_move() throws GameActionException {
		if (rc.senseOre(rc.getLocation()) > 1) {// there is ore, so try to mine
			if (rc.isCoreReady() && rc.canMine()) {
				rc.mine();
			}
		} else {// no ore, so look for ore
			move_around();
		}
	}

	private static void move_around() throws GameActionException {
		if (rand.nextDouble() < 0.05) {
			if (rand.nextDouble() < 0.5) {
				facing = facing.rotateLeft();
			} else {
				facing = facing.rotateRight();
			}
		}
		MapLocation tileInFront = rc.getLocation().add(facing);

		// check that the direction in front is not a tile that can be attacked
		// by the enemy towers
		MapLocation[] enemyTowers = rc.senseEnemyTowerLocations();
		boolean tileInFrontSafe = true;
		for (MapLocation m : enemyTowers) {
			if (m.distanceSquaredTo(tileInFront) <= RobotType.TOWER.attackRadiusSquared) {
				tileInFrontSafe = false;
				break;
			}
		}

		// check that we are not facing off the edge of the map
		if (rc.senseTerrainTile(tileInFront) != TerrainTile.NORMAL
				|| !tileInFrontSafe) {
			facing = facing.rotateLeft();
		} else {
			// try to move in the facing direction
			if (rc.isCoreReady() && rc.canMove(facing)) {
				rc.move(facing);
			}
		}
	}

	private static void build_unit(RobotType type, Direction d, int channel)
			throws GameActionException {
		if (d != null && rc.getTeamOre() > type.oreCost) {
			if (rc.isCoreReady() && rc.canBuild(d, type)) {
				rc.build(d, type);
				rc.broadcast(channel, rc.readBroadcast(channel) + 1);
			}
		}
	}

	private static void spawn_unit(RobotType type, Direction d)
			throws GameActionException {
		if (d != null && rc.isCoreReady() && rc.canSpawn(d, type)) {
			rc.spawn(d, type);
		}
	}

	private static void spawn_unit(RobotType type, Direction d, int channel)
			throws GameActionException {
		if (d != null && rc.isCoreReady() && rc.canSpawn(d, type)) {
			rc.spawn(d, type);
			rc.broadcast(channel, rc.readBroadcast(channel) + 1);
		}
	}

	private static MapLocation location_from_channel(int one, int two)
			throws GameActionException {
		return new MapLocation(rc.readBroadcast(one), rc.readBroadcast(two));
	}

	private static boolean low_attack_density(BaseBot bot) {
		return rc.senseNearbyRobots(rc.getLocation(), 30, bot.myTeam).length < 10;
	}

	private static int id_to_channel(int id) {
		return (id % 3500) + 3000;
	}

	static int direction_to_int(Direction d) {
		switch (d) {
		case NORTH:
			return 0;
		case NORTH_EAST:
			return 1;
		case EAST:
			return 2;
		case SOUTH_EAST:
			return 3;
		case SOUTH:
			return 4;
		case SOUTH_WEST:
			return 5;
		case WEST:
			return 6;
		case NORTH_WEST:
			return 7;
		default:
			return -1;
		}
	}

	static private int manhattan_distance(MapLocation a, MapLocation b) {
		return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
	}

	static private MapLocation[] get_neighbors(MapLocation loc) {
		MapLocation[] neighbors = new MapLocation[8];
		for (int i = 0; i < directions.length; i++) {
			neighbors[i] = loc.add(directions[i]);
		}
		return neighbors;
	}

	private static MapLocation halfway_between(MapLocation one, MapLocation two) {
		return new MapLocation((one.x + two.x) / 2, (one.y + two.y) / 2);
	}

	static private Queue<MapLocation> reconstruct_path(
			Map<MapLocation, MapLocation> came_from, MapLocation start,
			MapLocation finish) {
		MapLocation current = finish;
		List<MapLocation> path = new LinkedList<MapLocation>();
		while (true) {
			current = came_from.get(current);
			if (current.equals(start)) {
				break;
			}
			path.add(current);
		}
		Collections.reverse(path);
		Queue<MapLocation> ret_path = new LinkedList<MapLocation>(path);
		return ret_path;
	}

	static Comparator<Tuple<MapLocation, Integer>> comparater = new Comparator<Tuple<MapLocation, Integer>>() {

		@Override
		public int compare(Tuple<MapLocation, Integer> o1,
				Tuple<MapLocation, Integer> o2) {
			if (o1.y < o2.y) {
				return -1;
			} else if (o1.y > o2.y) {
				return 1;
			} else {
				return 0;
			}
		}
	};

	static private MapLocation closer_finish(MapLocation start,
			MapLocation finish) {
		while (start.distanceSquaredTo(finish) > rc.getType().sensorRadiusSquared) {
			finish = halfway_between(start, finish);
		}
		return finish;
	}

	static private Queue<MapLocation> a_star_search(MapLocation start,
			MapLocation finish,
			PriorityQueue<Tuple<MapLocation, Integer>> frontier,
			Map<MapLocation, MapLocation> came_from,
			Map<MapLocation, Integer> cost_so_far) throws GameActionException {

		MapLocation[] my_towers = rc.senseTowerLocations();
		MapLocation[] enemy_towers = rc.senseEnemyTowerLocations();
		MapLocation current;
		boolean complete = false;
		int byte_code_limit = 300;
		while (!frontier.isEmpty()
				&& Clock.getBytecodesLeft() > byte_code_limit) {
			current = frontier.poll().x;

			if (current.equals(finish)) {
				complete = true;
				break;
			}

			MapLocation[] neighbors = get_neighbors(current);
			MapLocation next;
			int new_cost, priority;
			int added_cost;
			for (int i = 0; i < neighbors.length; i++) {
				added_cost = 6;
				next = neighbors[i];
				if (!rc.canSenseLocation(next) || !rc.isLocationOccupied(next)) {

					if (rc.senseTerrainTile(next) == TerrainTile.NORMAL
							&& !Arrays.asList(enemy_towers).contains(next)
							&& !Arrays.asList(my_towers).contains(next)) {

						// for (MapLocation m : enemy_towers) {
						// if (m.distanceSquaredTo(next) <=
						// RobotType.TOWER.attackRadiusSquared) {
						// added_cost += 1;
						// }
						// }
						//
						// for (MapLocation m : my_towers) {
						// if (m.distanceSquaredTo(next) <=
						// RobotType.TOWER.attackRadiusSquared) {
						// added_cost += -1;
						// }
						// }

						// check that we are not facing off the edge of the map
						new_cost = cost_so_far.get(current) + added_cost;
						if (!cost_so_far.containsKey(next)
								|| new_cost < cost_so_far.get(next)) {
							cost_so_far.put(next, new_cost);
							priority = new_cost
									+ manhattan_distance(next, finish);
							frontier.add(new Tuple<MapLocation, Integer>(next,
									priority));
							came_from.put(next, current);
						}
					}
				}
			}
		}
		if (complete) {
			return reconstruct_path(came_from, start, finish);
		} else {
			return null;
		}
	}
}

class Tuple<X, Y> {
	public final X x;
	public final Y y;

	public Tuple(X x, Y y) {
		this.x = x;
		this.y = y;
	}
}
