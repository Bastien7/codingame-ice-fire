import BuildingType.*
import CaseType.*
import java.util.*


fun debug(value: Any?) = System.err.println(value)


data class Point(val x: Int, val y: Int) {
    fun distanceTo(other: Point) = Math.abs(this.x - other.x) + Math.abs(this.y - other.y)
    fun squareDistanceTo(other: Point) = ((this.x - other.x) * (this.x - other.x)) + ((this.y - other.y) * (this.y - other.y))
    fun isAdjacent(point: Point) = Math.abs(this.x - point.x) <= 1 && Math.abs(this.y - point.y) <= 1

    val left get() = Point(this.x - 1, this.y)
    val right get() = Point(this.x + 1, this.y)
    val up get() = Point(this.x, this.y + 1)
    val down get() = Point(this.x, this.y - 1)
}

enum class BuildingType {
    QG, MINE, TOWER
}

enum class CaseType {
    VOID, NEUTRAL, ALLY_DISABLED, ALLY_ENABLED, ENEMY_DISABLED, ENEMY_ENABLED
}

data class Case(val position: Point, var type: CaseType) {
    fun adjacentCase(game: Game): List<Case> {
        return listOf(this.position.down, this.position.up, this.position.left, this.position.right)
            .filter { it.x in 0..11 && it.y in 0..11 }
            .map { game.get(it) }
    }
}

data class Player(val units: MutableList<Unit> = mutableListOf(), var gold: Int = 0, var income: Int = 0)

data class Unit(val id: Int, var level: Int = 1, var position: Point, var isReady: Boolean = false)

data class Building(val position: Point, val type: BuildingType, val owner: Player)

data class MineSpot(val position: Point, var targetedBy: Unit? = null)

data class Game(val myPlayer: Player = Player(), val enemy: Player = Player(), var cases: List<List<Case>> = listOf(), var buildings: List<Building> = listOf(), val mineSpots: List<MineSpot>) {
    fun getOrNull(point: Point): Case? = cases.getOrNull(point.y)?.getOrNull(point.x)
    fun getOrNull(x: Int, y: Int): Case? = cases.getOrNull(y)?.getOrNull(x)
    fun get(point: Point): Case = cases[point.y][point.x]
    fun get(building: Building): Case = cases[building.position.y][building.position.x]
    fun anyUnitOn(case: Case) = anyUnitOn(case.position)
    fun anyUnitOn(position: Point) = (myPlayer.units + enemy.units).any { it.position == position }

    fun update(input: Scanner) {
        myPlayer.gold = input.nextInt()
        myPlayer.income = input.nextInt()

        enemy.gold = input.nextInt()
        enemy.income = input.nextInt()

        cases = (0 until 12).map { y ->
            val line = input.next()
            System.err.println()
            (0 until 12).map { x ->
                val caseType = when (line[x]) {
                    '#' -> VOID
                    '.' -> NEUTRAL
                    'O' -> ALLY_ENABLED
                    'o' -> ALLY_DISABLED
                    'X' -> ENEMY_ENABLED
                    'x' -> ENEMY_DISABLED
                    else -> error("What is this case type? ${line[x]}")
                }
                //System.err.print(line[x])
                Case(Point(x, y), caseType)
            }
        }
        //System.err.println()

        this.buildings = (0 until input.nextInt()).map {
            val ownerInt = input.nextInt()
            val owner = if (ownerInt == 0) myPlayer else enemy
            val buildingType = when (input.nextInt()) {
                0 -> QG
                1 -> MINE
                2 -> TOWER
                else -> error("What's this building type?")
            }
            val x = input.nextInt()
            val y = input.nextInt()

            Building(Point(x, y), buildingType, owner)
        }

        val unitCount = input.nextInt()
        val existentIds = (0 until unitCount).map {
            val owner = input.nextInt()
            val unitId = input.nextInt()
            val level = input.nextInt()
            val position = Point(input.nextInt(), input.nextInt())

            val playerOwner = if (owner == 0) myPlayer else enemy

            playerOwner.units.find { it.id == unitId }?.apply {
                this.level = level
                this.position = position
                this.isReady = true
            } ?: playerOwner.units.add(Unit(unitId, level, position, true))

            unitId
        }
        myPlayer.units.removeAll { it.id !in existentIds }
        enemy.units.removeAll { it.id !in existentIds }
    }
}

/**
 *
 *
 * PATH FINDER
 *
 *
 */
object PathFinder {
    private data class Node(val case: Case, val cost: Int, val estimatedCost: Int, val previousNode: Node?)

    private fun getAdjacentCases(map: Game, case: Case): List<Case> {
        val position = case.position
        return listOfNotNull(map.getOrNull(position.left), map.getOrNull(position.right), map.getOrNull(position.down), map.getOrNull(position.up)).filter { it.type != VOID }
    }

    private fun getAdjacentNodes(map: Game, end: Point, openList: MutableList<Node>, closedList: MutableList<Node>, node: Node): MutableList<Node> {
        return getAdjacentCases(map, node.case)
            .filter { case -> openList.find { node -> node.case == case } == null }
            .filter { case -> closedList.find { node -> node.case == case } == null }
            .map { Node(it, node.cost + 1, it.position.distanceTo(end), node) }
            .toMutableList()
    }

    private fun extractPath(latestNode: Node): List<Case> {
        val path: MutableList<Case> = mutableListOf()
        var currentNode: Node = latestNode
        while (currentNode.previousNode != null) {
            path.add(currentNode.case)
            currentNode = currentNode.previousNode!!
        }
        return path
    }

    fun findPath(map: Game, start: Point, end: Point): List<Case>? {
        if (start == end) return listOf()

        val closedList = mutableListOf<Node>()
        val firstNodes = getAdjacentNodes(map, end, mutableListOf(), closedList, Node(map.get(start), 0, start.distanceTo(end), null))

        return findPath(map, end, firstNodes, mutableListOf())
    }

    private fun findPath(map: Game, end: Point, openList: MutableList<Node>, closedList: MutableList<Node>): List<Case>? {
        var bestNode: Node? = null

        while (bestNode?.estimatedCost != 0 && !openList.isEmpty()) {
            bestNode = openList.sortedBy { it.cost + it.estimatedCost }.firstOrNull()
            if (bestNode == null) {
                return null
            }
            openList.remove(bestNode)
            closedList.add(bestNode)
            openList.addAll(getAdjacentNodes(map, end, openList, closedList, bestNode))
        }

        if (bestNode == null)
            return null
        else
            return extractPath(bestNode)
    }
}

fun Unit.move(destination: Point) = print("MOVE ${this.id} ${destination.x} ${destination.y};")
fun Unit.move(case: Case) = this.move(case.position)
fun Unit.move(mine: MineSpot) = this.move(mine.position)
fun Unit.attack(building: Building) = this.move(building.position)
fun Unit.attack(unit: Unit) = this.move(unit.position)
fun train(level: Int, position: Point) = print("TRAIN $level ${position.x} ${position.y};")
fun train(level: Int, case: Case) = train(level, case.position)
fun build(mine: MineSpot) = print("BUILD MINE ${mine.position.x} ${mine.position.y};")
fun buildTower(case: Case) = print("BUILD TOWER ${case.position.x} ${case.position.y};")
fun waitForLife() = print("WAIT;")


fun Unit.findClosestNeutralMine(game: Game): MineSpot? {
    return game.mineSpots.filter { mine -> game.get(mine.position).type == NEUTRAL && !game.anyUnitOn(mine.position) && mine.targetedBy == null }.minBy { it.position.distanceTo(this.position) }
}

fun Unit.findClosestNeutralCase(game: Game, enemyQG: Building): Case {
    val closestNeutralCases = game.cases.flatten().filter { it.type != ALLY_ENABLED && it.type != VOID }
        .groupBy { it.position.distanceTo(this.position) }
        .minBy { it.key }?.value

    debug("$id explore ${closestNeutralCases?.map { it.position }}")

    return closestNeutralCases?.maxBy { game.myPlayer.units.sumBy { it.position.distanceTo(this.position) } }
        ?: game.get(enemyQG)
}

fun Game.findMineSpotAvailable(): MineSpot? {
    val playerQG = this.buildings.first { it.owner == this.myPlayer && it.type == QG }

    return this.mineSpots
        .filter { mine -> this.get(mine.position).type == ALLY_ENABLED && !anyUnitOn(mine.position) && this.buildings.none { it.position == mine.position && it.owner == this.myPlayer } }
        .sortedBy { it.position.distanceTo(playerQG.position) }
        .firstOrNull()
}

fun Game.findCaseToTrainLevel1(enemyQG: Building): MutableList<Case> {
    return this.cases.asSequence().flatten()
        .filter { case -> case.type == ALLY_ENABLED }
        .map { it.adjacentCase(this) }
        .flatten().distinct()
        .filter { case -> case.type != VOID && myPlayer.units.none { it.position == case.position } && enemy.units.none { it.position == case.position } && this.buildings.none { it.position == case.position && it.type != QG && it.owner != this.enemy } }
        .sortedBy { it.position.distanceTo(enemyQG.position) }
        //.sortedBy { PathFinder.findPath(this, it.position, enemyQG.position)?.size ?: Int.MAX_VALUE }
        .toMutableList()
}

fun Game.findCaseToTrainLevel2(enemyQG: Building): List<Case> {
    return this.cases.asSequence().flatten()
        .filter { case -> case.type == ALLY_ENABLED }
        .map { it.adjacentCase(this) }
        .flatten().distinct()
        .filter { case -> case.type != VOID && this.enemy.units.any { it.level == 1 && it.position == case.position } }
        .sortedBy { it.position.distanceTo(enemyQG.position) }
        .toList()
}

fun Game.findCaseToTrainLevel3(enemyQG: Building): List<Case> {
    return this.cases.asSequence().flatten()
        .filter { case -> case.type == ALLY_ENABLED }
        .map { it.adjacentCase(this) }
        .flatten().distinct()
        .filter { case -> case.type != VOID && this.enemy.units.any { it.position == case.position } }
        .sortedBy { it.position.distanceTo(enemyQG.position) }
        .toList()
}


fun Game.findCaseToBuildTower(myTowers: List<Building>, target: Building): Case? {
    val towerCases = myTowers.flatMap {
        val x = it.position.x
        val y = it.position.y

        listOfNotNull(
            this.getOrNull(x - 1, y - 1),
            this.getOrNull(x, y - 1),
            this.getOrNull(x + 1, y - 1),
            this.getOrNull(x - 1, y),
            this.getOrNull(x + 1, y),
            this.getOrNull(x - 1, y + 1),
            this.getOrNull(x, y + 1),
            this.getOrNull(x + 1, y + 1)
        )
    }
    return (this.cases.flatten() - towerCases)
        .filter { case -> case.type == ALLY_ENABLED && !anyUnitOn(case) && this.buildings.noneOn(case) }
        .sortedBy { it.position.distanceTo(target.position) }
        .firstOrNull()
}

fun Game.findCaseToBuildTowerAggressive(myTowers: List<Building>, target: Building): Case? {
    val towerCases = myTowers.flatMap {
        val x = it.position.x
        val y = it.position.y

        listOfNotNull(
            this.getOrNull(x - 1, y - 1),
            this.getOrNull(x, y - 1),
            this.getOrNull(x + 1, y - 1),
            this.getOrNull(x - 1, y),
            this.getOrNull(x + 1, y),
            this.getOrNull(x - 1, y + 1),
            this.getOrNull(x, y + 1),
            this.getOrNull(x + 1, y + 1)
        )
    }
    return (this.cases.flatten() - towerCases)
        .filter { case -> case.type == ALLY_ENABLED && !anyUnitOn(case) && this.buildings.noneOn(case) && case.adjacentCase(this).any { it.type == ENEMY_ENABLED } }
        .sortedBy { it.position.distanceTo(target.position) }
        .firstOrNull()
}


fun List<Unit>.noneIn(case: Case) = this.none { it.position == case.position }
fun List<Building>.noneOn(case: Case) = this.none { it.position == case.position }

fun List<Unit>.attack(attackableUnits: List<Unit>, enemyQG: Building) {
    if (attackableUnits.isNotEmpty()) {
        debug("attack guy")
        this.forEach { hunter -> hunter.attack(attackableUnits.minBy { it.position.distanceTo(hunter.position) }!!) }
    } else {
        debug("attack qg $this")
        this.sortedBy { it.position.distanceTo(enemyQG.position) }.forEach {
            debug("${it.id} will attack $enemyQG")
            it.attack(enemyQG)
        }
    }
}

fun List<Unit>.conquer(mines: List<Building>, enemyQG: Building) {
    if (mines.isNotEmpty()) {
        val sortedUnits = this.sortedBy { it.position.distanceTo(enemyQG.position) }
        val groups = sortedUnits.groupBy { unit ->
            val closestMine = mines.minBy { it.position.distanceTo(unit.position) }!!
            unit.position.distanceTo(closestMine.position) < unit.position.distanceTo(enemyQG.position)
        }
        groups[true]?.forEach { hunter -> hunter.attack(mines.minBy { it.position.distanceTo(hunter.position) }!!) }
        groups[false]?.forEach { it.attack(enemyQG) }
    } else {
        this.sortedBy { it.position.distanceTo(enemyQG.position) }.forEach { it.attack(enemyQG) }
    }
}

fun Unit.explore(game: Game, enemyQG: Building) {
    val closestNeutralMine = this.findClosestNeutralMine(game)
    if (closestNeutralMine != null) {
        this.move(closestNeutralMine)
        closestNeutralMine.targetedBy = this
    } else {
        val neutralCase = this.findClosestNeutralCase(game, enemyQG)
        this.move(neutralCase)
        neutralCase.type = ALLY_ENABLED
    }
}


abstract class Strategy(val game: Game) {
    abstract fun play(turn: Int)
}

abstract class OptionalStrategy(game: Game) : Strategy(game) {
    abstract fun isNeeded(): Boolean
}

class StrategyConquer(game: Game, private val explorers: MutableList<Unit> = mutableListOf()) : Strategy(game) {
    override fun play(turn: Int) {
        val myPlayer = game.myPlayer
        val myPlayerMines = game.buildings.filter { it.owner == myPlayer && it.type == MINE }
        val enemyQG = game.buildings.first { it.owner == game.enemy && it.type == QG }

        val mineToBuild = game.findMineSpotAvailable()

        if (mineToBuild != null && explorers.size >= 3 && myPlayer.gold >= 20 + myPlayerMines.size * 4) {
            build(mineToBuild)
            myPlayer.gold -= 20 + myPlayerMines.size * 4
            myPlayer.income += 4
        }

        myPlayer.units.forEach {
            //it.explore(game, enemyQG)
            val neutralCase = it.findClosestNeutralCase(game, enemyQG)
            it.move(neutralCase)
            it.position = neutralCase.position
            neutralCase.type = ALLY_ENABLED
        }

        var casesToTrain = game.findCaseToTrainLevel1(enemyQG)

        while (explorers.size < 7 && casesToTrain.isNotEmpty() && myPlayer.gold >= 10) {
            val caseToTrain = casesToTrain.first()
            train(1, caseToTrain)
            myPlayer.units.add(Unit(-1, 1, caseToTrain.position))
            caseToTrain.type = ALLY_ENABLED
            myPlayer.gold -= 10
            myPlayer.income -= 1
            casesToTrain = game.findCaseToTrainLevel1(enemyQG)
        }

        game.mineSpots.forEach { it.targetedBy = null }
    }
}

class StrategyAttack(game: Game) : Strategy(game) {
    override fun play(turn: Int) {
        val myPlayer = game.myPlayer
        val enemy = game.enemy
        val myPlayerMines = game.buildings.filter { it.owner == myPlayer && it.type == MINE }
        val myPlayerTowers = game.buildings.filter { it.owner == myPlayer && it.type == TOWER }
        val enemyQG = game.buildings.first { it.owner == game.enemy && it.type == QG }
        val enemyMines = game.buildings.filter { it.owner == game.enemy && it.type == MINE }

        val casesToBuildTower = game.findCaseToBuildTowerAggressive(myPlayerTowers, enemyQG)
        val mineToBuild = game.findMineSpotAvailable()


        val unitsLevel1 = myPlayer.units.filter { it.level == 1 && it.isReady }
        val explorer = unitsLevel1.firstOrNull()
        val unitsLevel1Attackers = if (explorer == null) unitsLevel1 else unitsLevel1 - explorer
        val unitsLevel2 = myPlayer.units.filter { it.level == 2 && it.isReady }
        val unitsLevel3 = myPlayer.units.filter { it.level == 3 && it.isReady }

        debug("my units: ${myPlayer.units}")

        val enemyUnitsLevel1 = enemy.units.filter { it.level == 1 && it.isReady }
        val enemyUnitsLevel2 = enemy.units.filter { it.level == 2 && it.isReady }

        unitsLevel3.attack(enemyUnitsLevel2, enemyQG)
        unitsLevel2.attack(enemyUnitsLevel1, enemyQG)
        unitsLevel1Attackers.conquer(enemyMines, enemyQG)
        explorer?.explore(game, enemyQG)

        var casesToTrainLevel2 = game.findCaseToTrainLevel2(enemyQG)
        debug("${casesToTrainLevel2.size} cases to train level2")
        //Level 2
        while (casesToTrainLevel2.isNotEmpty() && myPlayer.gold >= 20 && myPlayer.income > 4) {
            train(2, casesToTrainLevel2.first())
            myPlayer.units.add(Unit(-1, 2, casesToTrainLevel2.first().position))
            myPlayer.gold -= 20
            myPlayer.income -= 4
            casesToTrainLevel2 = game.findCaseToTrainLevel2(enemyQG)
            debug("then ${casesToTrainLevel2.size} cases to train level2")
        }

        //Tower
        if (casesToBuildTower != null && myPlayer.gold >= 15 && myPlayer.income >= 5) {
            buildTower(casesToBuildTower)
            game.buildings = game.buildings.toMutableList() + Building(casesToBuildTower.position, TOWER, myPlayer)
            myPlayer.gold -= 15
        }

        //Mine
        if (mineToBuild != null && myPlayer.gold >= 20 + myPlayerMines.size * 4) {
            build(mineToBuild)
            myPlayer.gold -= 20 + myPlayerMines.size * 4
            myPlayer.income += 4
        }

        val casesToTrainLevel3 = game.findCaseToTrainLevel3(enemyQG)
        debug("${casesToTrainLevel2.size} cases to train level3")

        //Level 3
        if (casesToTrainLevel3.isNotEmpty() && myPlayer.gold >= 50 && myPlayer.income >= 35) {
            train(3, casesToTrainLevel3.first())
            myPlayer.units.add(Unit(-1, 3, casesToTrainLevel3.first().position))
            myPlayer.gold -= 30
            myPlayer.income -= 20
            //casesToTrainLevel3 = game.findCaseToTrainLevel3(enemyQG)
            debug("then ${casesToTrainLevel2.size} cases to train level3")
        }

        var casesToTrain = game.findCaseToTrainLevel1(enemyQG)
        debug("${casesToTrain.size} to train level1's")
        //Level 1
        while (casesToTrain.isNotEmpty() && myPlayer.gold >= 10) {
            train(1, casesToTrain.first())
            myPlayer.units.add(Unit(-1, 1, casesToTrain.first().position))
            myPlayer.gold -= 10
            myPlayer.income -= 1
            casesToTrain = game.findCaseToTrainLevel1(enemyQG)
            debug("then ${casesToTrainLevel2.size} cases to train level1")
        }

        debug("My explorer is ${explorer?.id}")
    }
}

class StrategyInstantKill(game: Game) : OptionalStrategy(game) {
    override fun isNeeded(): Boolean {
        val enemyQG = game.buildings.first { it.owner == game.enemy && it.type == QG }
        return game.myPlayer.units.any { it.position.distanceTo(enemyQG.position) <= (game.myPlayer.gold / 10) }
    }

    override fun play(turn: Int) {
        debug("instant kill that guy!")
        val enemyQG = game.buildings.first { it.owner == game.enemy && it.type == QG }
        val closestUnit = game.myPlayer.units.map { it.position.distanceTo(enemyQG.position) }.max() ?: 0

        (0..closestUnit).forEach {
            //debug("cases: ${game.findCaseToTrainLevel1(enemyQG).map { "${it.position.x} ${it.position.y}" }}")
            val case = game.findCaseToTrainLevel1(enemyQG).minBy { it.position.squareDistanceTo(enemyQG.position) }
            debug("best case: ${case?.position?.x} ${case?.position?.y}")
            if (case != null) {
                train(1, case)
                game.myPlayer.units.add(Unit(-1, 1, case.position))
                case.type = ALLY_ENABLED
            }
        }
    }
}

class StrategyTowerDefense(game: Game) : OptionalStrategy(game) {
    override fun isNeeded(): Boolean {
        val playerQG = game.buildings.first { it.owner == game.myPlayer && it.type == QG }
        return game.enemy.units.any { it.position.distanceTo(playerQG.position) <= ((game.enemy.income + game.enemy.gold) / 10) }
    }

    override fun play(turn: Int) {
        val myPlayer = game.myPlayer
        val playerQG = game.buildings.first { it.owner == myPlayer && it.type == QG }
        val myPlayerTowers = game.buildings.filter { it.owner == myPlayer && it.type == TOWER }

        val casesToBuildTower = game.findCaseToBuildTower(myPlayerTowers, playerQG)

        if (casesToBuildTower != null) {
            buildTower(casesToBuildTower)
        }
    }
}

class StrategyKillLevel3(game: Game) : OptionalStrategy(game) {
    override fun isNeeded(): Boolean {
        return game.enemy.units.any { it.level == 3 }
    }

    override fun play(turn: Int) {
        val guysToKill = game.enemy.units
            .filter {
                it.level == 3 && game.get(it.position).adjacentCase(game).any { case ->
                    game.myPlayer.units.find { it.position == case.position } != null || game.buildings.find { it.position == case.position && it.owner == game.myPlayer } != null
                }
            }
        val killedGuys = mutableListOf<Unit>()

        guysToKill.forEach { enemyUnit ->
            val safeCases = game.get(enemyUnit.position).adjacentCase(game).filter { it.type == ENEMY_ENABLED && !game.anyUnitOn(it) }
            if (game.myPlayer.gold >= 10 && safeCases.size == 1 && safeCases.first().adjacentCase(game).any { it.type == ALLY_ENABLED }) {
                val caseToTrain = safeCases.first()
                val blockedByTower = game.buildings.any { it.owner == game.enemy && it.type == TOWER && it.position.distanceTo(caseToTrain.position) <= 1 }

                if (!blockedByTower) {
                    train(1, caseToTrain)
                    game.myPlayer.gold -= 10
                    killedGuys.add(enemyUnit)
                    debug("kill that guy3 at ${caseToTrain.position}")
                } else {
                    debug("${caseToTrain.position} is blocked by a enemy tower")
                }
            }
        }

        (guysToKill - killedGuys).forEach { enemyUnit ->
            val enemyCase = game.get(enemyUnit.position)
            val canPopOnEnemy = enemyCase.adjacentCase(game).any { it.type == ALLY_ENABLED }
            if (game.myPlayer.gold >= 30 && canPopOnEnemy) {
                train(3, enemyCase)
                game.myPlayer.gold -= 30
                debug("kill that guy3 at ${enemyCase.position}")
            }
        }
    }
}

fun main(args: Array<String>) {
    val input = Scanner(System.`in`)

    val mineSpots = (0 until input.nextInt()).map {
        val x = input.nextInt()
        val y = input.nextInt()
        MineSpot(Point(x, y))
    }

    val game = Game(mineSpots = mineSpots)
    var mainStrategy: Strategy = StrategyConquer(game)
    var turn = 0

    // game loop
    while (true) {
        turn++
        debug("Starting turn $turn")

        if (turn > 6)
            mainStrategy = StrategyAttack(game)

        game.update(input)
        debug("I have ${game.myPlayer.gold} golds")

        listOf(StrategyInstantKill(game), StrategyKillLevel3(game), StrategyTowerDefense(game))
            .filter { it.isNeeded() }.forEach { it.play(turn) }

        mainStrategy.play(turn)

        waitForLife()
        println()

    }
}