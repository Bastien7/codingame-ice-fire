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
    fun get(x: Int, y: Int): Case = cases[y][x]
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
                Case(Point(x, y), caseType)
            }
        }

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

    private fun getAdjacentCases(map: Game, case: Case, forbiddenCases: List<Case>): List<Case> {
        val position = case.position
        return listOfNotNull(map.getOrNull(position.left), map.getOrNull(position.right), map.getOrNull(position.down), map.getOrNull(position.up)).filter { it.type != VOID && it !in forbiddenCases }
    }

    private fun getAdjacentNodes(map: Game, end: Point, openList: MutableList<Node>, closedList: MutableList<Node>, node: Node, forbiddenCases: List<Case>): MutableList<Node> {
        return getAdjacentCases(map, node.case, forbiddenCases)
            .filter { case -> openList.find { node -> node.case == case } == null }
            .filter { case -> closedList.find { node -> node.case == case } == null }
            .map { Node(it, node.cost + 1, it.position.distanceTo(end), node) }
            .toMutableList()
    }

    private fun extractPath(latestNode: Node): List<Case> {
        val path: MutableList<Case> = mutableListOf()
        var currentNode: Node = latestNode
        while (currentNode.previousNode != null) {
            path.add(0, currentNode.case)
            currentNode = currentNode.previousNode!!
        }
        return path
    }

    fun findPath(map: Game, start: Point, end: Point, forbiddenCases: List<Case> = listOf()): List<Case>? {
        if (start == end) return listOf()

        val closedList = mutableListOf<Node>()
        val firstNodes = getAdjacentNodes(map, end, mutableListOf(), closedList, Node(map.get(start), 0, start.distanceTo(end), null), forbiddenCases)

        return findPath(map, end, firstNodes, mutableListOf(), forbiddenCases)
    }

    private fun findPath(map: Game, end: Point, openList: MutableList<Node>, closedList: MutableList<Node>, forbiddenCases: List<Case>): List<Case>? {
        var bestNode: Node? = null

        while (bestNode?.estimatedCost != 0 && openList.isNotEmpty()) {
            bestNode = openList.sortedBy { it.cost + it.estimatedCost }.firstOrNull()
            if (bestNode == null) {
                return null
            }
            openList.remove(bestNode)
            closedList.add(bestNode)
            openList.addAll(getAdjacentNodes(map, end, openList, closedList, bestNode, forbiddenCases))
        }

        if (bestNode == null || bestNode.case.position != end)
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
fun buildTower(case: Case) = buildTower(case.position)
fun buildTower(position: Point) = print("BUILD TOWER ${position.x} ${position.y};")
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
        .sortedBy { it.position.squareDistanceTo(playerQG.position) }
        .firstOrNull()
}

fun Game.findCaseToTrainLevel1(enemyQG: Building): MutableList<Case> {
    return this.cases.asSequence().flatten()
        .filter { case -> case.type == ALLY_ENABLED }
        .map { it.adjacentCase(this) }
        .flatten().distinct()
        .filter { case -> case.type != VOID && myPlayer.units.none { it.position == case.position } && enemy.units.none { it.position == case.position } && this.buildings.none { it.position == case.position && it.type != QG && it.owner != this.enemy } }
        .sortedBy { it.position.squareDistanceTo(enemyQG.position) }
        //.sortedBy { PathFinder.findPath(this, it.position, enemyQG.position)?.size ?: Int.MAX_VALUE }
        .toMutableList()
}

fun Game.findCaseToTrainLevel2(enemyTowers: List<Building>, enemyQG: Building): List<Case> {
    return this.cases.asSequence().flatten()
        .filter { case -> case.type == ALLY_ENABLED }
        .map { it.adjacentCase(this) }
        .flatten().distinct()
        .filter { case -> case.type != VOID && this.enemy.units.any { it.level == 1 && it.position == case.position } && enemyTowers.none { it.position.distanceTo(case.position) <= 1 } }
        .sortedBy { it.position.squareDistanceTo(enemyQG.position) }
        .toList()
}

fun Game.findCaseToTrainLevel3(enemyQG: Building): List<Case> {
    return this.cases.asSequence().flatten()
        .filter { case -> case.type == ALLY_ENABLED }
        .map { it.adjacentCase(this) }
        .flatten().distinct()
        .filter { case -> case.type != VOID && this.enemy.units.any { it.position == case.position } }
        .sortedBy { it.position.squareDistanceTo(enemyQG.position) }
        .toList()
}


fun Game.findCaseToBuildTower(target: Building): Case? {
    val towerCases = this.buildings.filter { it.owner == myPlayer && it.type == TOWER }.flatMap {
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
        .filter { case -> case.type == ALLY_ENABLED && !anyUnitOn(case) && this.buildings.noneOn(case) && this.mineSpots.none { it.position == case.position } }
        .sortedBy { it.position.squareDistanceTo(target.position) }
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
        .sortedBy { it.position.squareDistanceTo(target.position) }
        .firstOrNull()
}


fun Game.findWeakPoints(enemyQG: Building): Case? {
    val riskyUnits: List<Unit>
    if (this.myPlayer.units.count { it.level > 1 } >= 2) {
        riskyUnits = this.myPlayer.units.filter { it.level >= 2 && this.get(it.position).adjacentCase(this).filter { it.type == ALLY_ENABLED }.size == 1 }
    } else {
        riskyUnits = this.myPlayer.units.filter { this.get(it.position).adjacentCase(this).filter { it.type == ALLY_ENABLED }.size == 1 }
    }
    val myTowersCases = this.buildings.filter { it.type == TOWER && it.owner == this.myPlayer }.map { this.get(it.position).adjacentCase(this) + this.get(it.position) }.flatten().filter { it.type == ALLY_ENABLED }

    return riskyUnits.mapNotNull { unit ->
        var currentCase = this.get(unit.position)
        var oldCase: Case = currentCase
        var risk = 0
        var caseToProtect: Case? = null
        var adjacentFriendlyCases = currentCase.adjacentCase(this).filter { it.type == ALLY_ENABLED }

        do {
            risk++
            if (risk > 5) {
                break //safety measure
            }
            val temp = currentCase
            currentCase = adjacentFriendlyCases.find { it != oldCase } ?: break
            oldCase = temp

            if (!anyUnitOn(currentCase) && currentCase !in myTowersCases) {
                caseToProtect = currentCase
                break
            }
            adjacentFriendlyCases = currentCase.adjacentCase(this).filter { it.type == ALLY_ENABLED }
        } while (adjacentFriendlyCases.size == 1 || (adjacentFriendlyCases.size <= 2 && oldCase in adjacentFriendlyCases))

        caseToProtect
    }.minBy { it.position.squareDistanceTo(enemyQG.position) }
}

data class KillPath(val unitsKilled: MutableList<Unit> = mutableListOf(), val path: MutableList<Case> = mutableListOf()) {

    fun getAttackPath(game: Game, forbiddenCases: List<Case>): List<Case>? {
        val allyCases = game.cases.flatten().filter { it.type == ALLY_ENABLED }

        val paths = this.path.flatMap { end ->
            val startCases = allyCases.sortedBy { start -> start.position.distanceTo(end.position) }.subList(0, Math.min(allyCases.size, 3))
            startCases.map { start -> PathFinder.findPath(game, start.position, end.position, forbiddenCases) }
        }.filterNotNull()

        //debug("killPath cases: ${this.path.map { it.position }}")
        //paths.forEach { debug("Path: ${it.map { it.position }}") }
        return paths.minBy { it.size }
    }
}

fun Game.findCriticalPoint(): List<KillPath> {
    val riskyUnits = this.enemy.units.filter { this.get(it.position).adjacentCase(this).filter { it.type == ENEMY_ENABLED }.size == 1 }

    return riskyUnits.map { unit ->
        var currentCase = this.get(unit.position)
        var oldCase: Case = currentCase
        var adjacentFriendlyCases = currentCase.adjacentCase(this).filter { it.type == ENEMY_ENABLED }
        var currentKillPath = KillPath(unitsKilled = mutableListOf(unit))
        val killPaths = mutableListOf<KillPath>()
        var risk = 0

        do {
            risk++
            if (risk > 6) {
                break //safety measure
            }

            val temp = currentCase
            currentCase = adjacentFriendlyCases.find { it != oldCase } ?: break
            oldCase = temp

            val unitOnPathStep = enemy.units.find { it.position == currentCase.position }
            if (unitOnPathStep == null) {
                currentKillPath.path.add(currentCase)
            } else {
                if (currentKillPath.path.isNotEmpty()) {
                    killPaths.add(currentKillPath)
                    currentKillPath = KillPath((killPaths.last().unitsKilled + unitOnPathStep).toMutableList())
                } else {
                    currentKillPath.unitsKilled += unitOnPathStep
                }
            }

            adjacentFriendlyCases = currentCase.adjacentCase(this).filter { it.type == ENEMY_ENABLED }
        } while (adjacentFriendlyCases.size == 1 || (adjacentFriendlyCases.size <= 2 && oldCase in adjacentFriendlyCases))

        if (currentKillPath.path.isNotEmpty()) {
            killPaths.add(currentKillPath)
        }
        killPaths
    }.flatten()
}

/*
fun main() {
    val game = Game(cases = listOf(
        listOf(
            Case(Point(0, 0), NEUTRAL),
            Case(Point(1, 0), NEUTRAL),
            Case(Point(2, 0), ENEMY_ENABLED),
            Case(Point(3, 0), ENEMY_ENABLED),
            Case(Point(4, 0), ENEMY_ENABLED),
            Case(Point(5, 0), NEUTRAL)
        ),
        listOf(
            Case(Point(0, 1), NEUTRAL),
            Case(Point(1, 1), NEUTRAL),
            Case(Point(2, 1), ENEMY_ENABLED),
            Case(Point(3, 1), NEUTRAL),
            Case(Point(4, 1), ENEMY_ENABLED),
            Case(Point(5, 1), NEUTRAL)
        ),
        listOf(
            Case(Point(0, 2), NEUTRAL),
            Case(Point(1, 2), NEUTRAL),
            Case(Point(2, 2), NEUTRAL),
            Case(Point(3, 2), NEUTRAL),
            Case(Point(4, 2), NEUTRAL),
            Case(Point(5, 2), NEUTRAL)
        )
    ), mineSpots = listOf())

    //game.myPlayer.units.add(Unit(1, 1, Point(4, 1)))
    //game.enemy.units.add(Unit(2, 1, Point(2, 0)))
    game.enemy.units.add(Unit(2, 1, Point(2, 1)))
    game.enemy.units.add(Unit(3, 1, Point(3, 2)))
    val path = PathFinder.findPath(game, Point(1, 1), Point(3, 0), game.enemy.units.map { game.get(it.position) })?.reversed()
    //game.myPlayer.units.add(Unit(2, 1, Point(3, 0)))

    //debug(game.findCriticalPoint())
    debug("Path length: ${path?.size}")
    debug(path?.map { it.position })
}*/



fun List<Building>.noneOn(case: Case) = this.none { it.position == case.position }

fun List<Unit>.attack(attackableUnits: List<Unit>, enemyQG: Building) {
    if (attackableUnits.isNotEmpty()) {
        debug("attack guy")
        this.forEach { hunter -> hunter.attack(attackableUnits.minBy { it.position.distanceTo(hunter.position) }!!) }
    } else {
        debug("attack qg $this")
        this.sortedBy { it.position.squareDistanceTo(enemyQG.position) }.forEach {
            debug("${it.id} will attack $enemyQG")
            it.attack(enemyQG)
        }
    }
}

fun List<Unit>.attack3(attackableUnits: List<Unit>, enemyQG: Building) {
    this.forEach { unit ->
        if (attackableUnits.any { it.position.squareDistanceTo(unit.position) <= 2 }) {
            unit.attack(attackableUnits.minBy { it.position.squareDistanceTo(unit.position) }!!)
        } else {
            unit.attack(enemyQG)
        }
    }
}


fun List<Unit>.conquer(game: Game, enemyQG: Building) {
    val enemyTowerCases = game.buildings.filter { it.type == TOWER && it.owner == game.enemy }.map { game.get(it.position).adjacentCase(game) + game.get(it.position) }.flatten().filter { it.type == ENEMY_ENABLED }
    //val myTowersCases = game.buildings.filter { it.type == TOWER && it.owner == game.myPlayer }.map { game.get(it.position) }

    this.sortedBy { it.position.distanceTo(enemyQG.position) }.forEach { unit ->
        val availableNextCases = game.get(unit.position).adjacentCase(game)
            .filter { it.type != VOID && it !in enemyTowerCases && !game.anyUnitOn(it) /*&& it !in myTowersCases*/ } //TODO why this last condition?

        val bestCaseToCapture = availableNextCases.filter { it.type != ALLY_ENABLED }.minBy { it.position.squareDistanceTo(enemyQG.position) }
        val bestCaseToAttack = availableNextCases.minBy { it.position.squareDistanceTo(enemyQG.position) }

        if (bestCaseToCapture != null) {
            unit.move(bestCaseToCapture)
            bestCaseToCapture.type = ALLY_ENABLED
            unit.position = bestCaseToCapture.position
        } else if (bestCaseToAttack != null) {
            unit.move(bestCaseToAttack)
            unit.position = bestCaseToAttack.position
        } else {
            unit.attack(enemyQG)
        }
    }
}

fun Unit.explore(game: Game, enemyQG: Building) {
    /*val closestNeutralMine = this.findClosestNeutralMine(game)
    if (closestNeutralMine != null) {
        debug("explorer go on neutral mine")
        this.move(closestNeutralMine)
        closestNeutralMine.targetedBy = this
    } else {*/
    debug("explorer go on neutral case")
    val neutralCase = this.findClosestNeutralCase(game, enemyQG)
    this.move(neutralCase)
    neutralCase.type = ALLY_ENABLED
    //}
}


abstract class Strategy(val game: Game) {
    abstract fun play(turn: Int)
}

abstract class OptionalStrategy(game: Game, val name: String) : Strategy(game) {
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
        val enemyTowers = game.buildings.filter { it.owner == enemy && it.type == TOWER }

        val unitsLevel1 = myPlayer.units.filter { it.level == 1 && it.isReady }
        val explorer = unitsLevel1.firstOrNull()
        val unitsLevel1Attackers = if (explorer == null) unitsLevel1 else unitsLevel1 - explorer
        val unitsLevel2 = myPlayer.units.filter { it.level == 2 && it.isReady }
        val unitsLevel3 = myPlayer.units.filter { it.level == 3 && it.isReady }

        val enemyUnitsLevel1 = enemy.units.filter { it.level == 1 && it.isReady }
        val enemyUnitsLevel2 = enemy.units.filter { it.level == 2 && it.isReady }

        unitsLevel3.attack3(enemyUnitsLevel2, enemyQG)
        unitsLevel2.attack(enemyUnitsLevel1, enemyQG)
        unitsLevel1Attackers.conquer(game, enemyQG)
        explorer?.explore(game, enemyQG)

        bfg10k(game)

        val casesToBuildTower = game.findWeakPoints(enemyQG)
        //Tower
        debug("There is a weak point to protect at ${casesToBuildTower?.position}")
        if (casesToBuildTower != null && myPlayer.gold >= 15 && myPlayer.income >= 5) {
            buildTower(casesToBuildTower)
            game.buildings = game.buildings.toMutableList() + Building(casesToBuildTower.position, TOWER, myPlayer)
            myPlayer.gold -= 15
        }

        var casesToTrainLevel2 = game.findCaseToTrainLevel2(enemyTowers, enemyQG)
        debug("${casesToTrainLevel2.size} cases to train level2")
        //Level 2
        while (casesToTrainLevel2.isNotEmpty() && myPlayer.gold >= 20 && myPlayer.income > 4) {
            train(2, casesToTrainLevel2.first())
            myPlayer.units.add(Unit(-1, 2, casesToTrainLevel2.first().position))
            casesToTrainLevel2.first().type = ALLY_ENABLED
            myPlayer.gold -= 20
            myPlayer.income -= 4
            casesToTrainLevel2 = game.findCaseToTrainLevel2(enemyTowers, enemyQG)
            debug("then ${casesToTrainLevel2.size} cases to train level2")
        }

        if (myPlayer.gold < 10) return

        val mineToBuild = game.findMineSpotAvailable()
        //Mine
        if (mineToBuild != null && myPlayer.gold >= 20 + myPlayerMines.size * 4) {
            build(mineToBuild)
            myPlayer.gold -= 20 + myPlayerMines.size * 4
            myPlayer.income += 4
        }

        if (myPlayer.gold < 10) return

        val casesToTrainLevel3 = game.findCaseToTrainLevel3(enemyQG)
        debug("${casesToTrainLevel3.size} cases to train level3")

        //Level 3
        if (casesToTrainLevel3.isNotEmpty() && myPlayer.gold >= 30 && myPlayer.income >= 20) {
            train(3, casesToTrainLevel3.first())
            myPlayer.units.add(Unit(-1, 3, casesToTrainLevel3.first().position))
            casesToTrainLevel3.first().type = ALLY_ENABLED
            myPlayer.gold -= 30
            myPlayer.income -= 20
            //casesToTrainLevel3 = game.findCaseToTrainLevel3(enemyQG)
            debug("then ${casesToTrainLevel2.size} cases to train level3")
        }

        if (myPlayer.gold < 10) return

        var casesToTrain = game.findCaseToTrainLevel1(enemyQG)
        debug("${casesToTrain.size} to train level1's")
        //Level 1
        while (casesToTrain.isNotEmpty() && myPlayer.gold >= 10) {
            train(1, casesToTrain.first())
            myPlayer.units.add(Unit(-1, 1, casesToTrain.first().position))
            casesToTrain.first().type = ALLY_ENABLED
            myPlayer.gold -= 10
            myPlayer.income -= 1
            casesToTrain = game.findCaseToTrainLevel1(enemyQG)
            debug("then ${casesToTrainLevel2.size} cases to train level1")
        }

        debug("My explorer is ${explorer?.id}")
    }
}

fun bfg10k(game: Game) {
    if (game.myPlayer.gold < 10)
        return

    val myPlayer = game.myPlayer
    val killPaths = game.findCriticalPoint()
    val forbiddenCases = (myPlayer.units + game.enemy.units).map { game.get(it.position) } + game.buildings.map { game.get(it.position) }

    val attack = killPaths.mapNotNull {
        val attackPath = it.getAttackPath(game, forbiddenCases)
        debug("kill ${it.unitsKilled.sumBy { it.level }} (cost: ${attackPath?.size}) by going to ${attackPath?.map { it.position }}")
        if (attackPath != null) Pair(it.unitsKilled, attackPath) else null
    }.filter { it.second.size * 10 <= myPlayer.gold }.maxBy { it.first.size / it.second.size }

    if (attack != null) {
        debug("attack ${attack.second.map { it.position }}")
        attack.second.forEach { case ->
            train(1, case)
            myPlayer.units.add(Unit(-2, 1, case.position))
            case.type = ALLY_ENABLED
            myPlayer.gold -= 10
            myPlayer.income -= 1
        }
        attack.first.forEach { deadUnit ->
            game.enemy.units.remove(deadUnit)
            game.get(deadUnit.position).type = ENEMY_DISABLED
        }
    }
}

class StrategyInstantKill(game: Game) : OptionalStrategy(game, "instant kill") {
    override fun isNeeded(): Boolean {
        val enemyQG = game.buildings.first { it.owner == game.enemy && it.type == QG }
        return game.myPlayer.units.any { it.position.distanceTo(enemyQG.position) <= (game.myPlayer.gold / 10) }
    }

    override fun play(turn: Int) {
        debug("instant kill that guy!")
        val enemyQG = game.buildings.first { it.owner == game.enemy && it.type == QG }
        val closestUnit = game.myPlayer.units.map { it.position.distanceTo(enemyQG.position) }.min() ?: Int.MAX_VALUE

        var maxDistance = Int.MAX_VALUE

        for (it in 1..closestUnit) {
            val case = game.findCaseToTrainLevel1(enemyQG).minBy { it.position.squareDistanceTo(enemyQG.position) }
            debug("best case: ${case?.position?.x} ${case?.position?.y}")
            val distance = case?.position?.distanceTo(enemyQG.position) ?: maxDistance
            if (distance >= maxDistance) {
                debug("Sorry, the instant kill failed")
                break
            } else {
                maxDistance = distance
            }
            if (case != null && game.buildings.none { it.position == case.position && it.type != QG } && game.enemy.units.none { it.position == case.position }) {
                train(1, case)
                game.myPlayer.units.add(Unit(-1, 1, case.position))
                case.type = ALLY_ENABLED
            } else {
                break
            }
        }
    }
}

class StrategyInstantKill2(game: Game) : OptionalStrategy(game, "instant extra kill") {
    private var path: List<Case>? = null
    private var warPath: List<Case>? = null

    override fun isNeeded(): Boolean {
        val enemyQG = game.buildings.first { it.owner == game.enemy && it.type == QG }
        val bestUnit = game.myPlayer.units.minBy { it.position.distanceTo(enemyQG.position) }

        if (bestUnit != null) {
            val towerCases = game.buildings.filter { it.type == TOWER }.flatMap { game.get(it.position).adjacentCase(game) + game.get(it.position) }
            val unitCases = game.enemy.units.map { game.get(it.position) }
            path = PathFinder.findPath(game, bestUnit.position, enemyQG.position, towerCases + unitCases)
            val path = path

            if (path != null && path.size * 10 < game.myPlayer.gold) {
                return true
            } else {
                warPath = PathFinder.findPath(game, bestUnit.position, enemyQG.position)
                val cost = warPath?.sumBy { case ->
                    if (case in towerCases) {
                        30
                    } else if (case in unitCases) {
                        when (game.enemy.units.find { unit -> unit.position == case.position }?.level) {
                            1 -> 20
                            else -> 30
                        }
                    } else {
                        10
                    }
                }
                return cost != null && cost <= game.myPlayer.gold
            }
        } else {
            return false
        }
    }

    override fun play(turn: Int) {
        debug("instant kill that guy!")
        val enemyQG = game.buildings.first { it.owner == game.enemy && it.type == QG }
        val bestUnit = game.myPlayer.units.minBy { it.position.distanceTo(enemyQG.position) }!!

        val towerCases = game.buildings.filter { it.type == TOWER }.flatMap { game.get(it.position).adjacentCase(game) + game.get(it.position) }
        val unitCases = game.enemy.units.map { game.get(it.position) }

        val path = this.path
        val warPath = this.warPath

        if (path != null && path.size * 10 < game.myPlayer.gold) {
            path.forEach { train(1, it.position) }
        } else {
            warPath?.forEach { case ->
                if (case in towerCases) {
                    train(3, case)
                } else if (case in unitCases) {
                    when (game.enemy.units.find { unit -> unit.position == case.position }?.level) {
                        1 -> train(2, case)
                        else -> train(3, case)
                    }
                } else {
                    train(1, case)
                }
            }
        }
    }
}

class StrategyAntiOneshot(game: Game) : OptionalStrategy(game, "anti oneshot defense") {
    override fun isNeeded(): Boolean {
        val playerQG = game.buildings.first { it.type == QG && it.owner == game.myPlayer }
        return game.enemy.units.any { it.position.distanceTo(playerQG.position) <= (game.enemy.income + game.enemy.gold) / 10 }
    }

    override fun play(turn: Int) {
        val playerQG = game.buildings.first { it.type == QG && it.owner == game.myPlayer }

        val caseToBuildTower: Case?

        if (playerQG.position == Point(0, 0)) {
            if (game.get(1, 1).type == ALLY_ENABLED) {
                caseToBuildTower = game.get(1, 1)
            } else {
                caseToBuildTower = game.findCaseToBuildTower(playerQG)
            }
        } else {
            if (game.get(10, 10).type == ALLY_ENABLED) {
                caseToBuildTower = game.get(10, 10)
            } else {
                caseToBuildTower = game.findCaseToBuildTower(playerQG)
            }
        }

        if (caseToBuildTower != null) {
            if(game.buildings.find { it.position == caseToBuildTower.position && it.type == MINE } != null) {
                train(1, caseToBuildTower)
                game.myPlayer.units.add(Unit(-3, 1, caseToBuildTower.position))
                caseToBuildTower.type = ALLY_ENABLED
                game.myPlayer.gold -= 10
            } else {
                buildTower(caseToBuildTower)
            }
        }
    }
}

class StrategyPreventInsertion(game: Game) : OptionalStrategy(game, "prevent enemy insertion") {
    override fun isNeeded(): Boolean {
        if (game.myPlayer.gold < 15 || game.enemy.units.isEmpty()) {
            return false
        }

        val playerQG = game.buildings.first { it.type == QG && it.owner == game.myPlayer }
        val closestUnit = (game.enemy.units + game.myPlayer.units).filter { it.position.distanceTo(playerQG.position) <= 10 }.sortedBy { it.position.distanceTo(playerQG.position) }.firstOrNull()
        return game.enemy.units.contains(closestUnit)
    }

    override fun play(turn: Int) {
        val playerQG = game.buildings.first { it.type == QG && it.owner == game.myPlayer }
        val closestUnit = game.enemy.units.minBy { it.position.distanceTo(playerQG.position) }

        if (closestUnit != null) {
            val towerCase = game.cases.flatten()
                .filter { it.type == ALLY_ENABLED }
                .minBy { case -> case.position.squareDistanceTo(playerQG.position).toDouble() / 10 + case.position.squareDistanceTo(closestUnit.position).toDouble() }

            if(towerCase != null) {
                if (game.buildings.none { it.position == towerCase.position || (it.type == TOWER && it.owner == game.myPlayer && it.position.distanceTo(towerCase.position) == 1) }) {
                    buildTower(towerCase)
                    game.myPlayer.gold -= 15
                    game.buildings = game.buildings + Building(towerCase.position, TOWER, game.myPlayer)
                } else if(game.buildings.find { it.position == towerCase.position }?.type == MINE) {
                    train(1, towerCase)
                    game.myPlayer.gold -= 10
                    game.myPlayer.units.add(Unit(-3, 1, towerCase.position))
                } else {
                    debug("Cannot prevent enemy insertion at case ${towerCase.position}")
                }
            } else {
                debug("cancel prevent enemy insertion")
            }
        }
    }
}

class StrategyKillLevel3(game: Game) : OptionalStrategy(game, "kill level3") {
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


class StrategyAttackCritical(game: Game) : OptionalStrategy(game, "critical attack") {
    var killPaths: List<KillPath> = listOf()

    override fun isNeeded(): Boolean {
        val units = game.myPlayer.units
        debug("before")
        killPaths = game.findCriticalPoint().toMutableList()
        debug("after, killPaths.size: ${killPaths.size}")
        killPaths = killPaths.filter { killPath -> killPath.path.any { case -> units.any { it.position.distanceTo(case.position) == 1 } } }
        debug("after filter, killPaths.size: ${killPaths.size}")
        return killPaths.isNotEmpty()
    }

    override fun play(turn: Int) {
        val units = game.myPlayer.units

        this.killPaths.forEach { killPath ->
            var done = false
            for (case in killPath.path) {
                if (!done) {
                    for (unit in units) {
                        if (unit.position.distanceTo(case.position) == 1) {
                            debug("${unit.id} kills by going to ${case.position}, it kills ${killPath.unitsKilled.size} guys")
                            unit.move(case)
                            unit.position = case.position
                            case.type = ALLY_ENABLED
                            done = true
                            break
                        }
                    }
                }
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

        listOf(StrategyInstantKill2(game), StrategyKillLevel3(game), StrategyAntiOneshot(game), StrategyPreventInsertion(game)).filter { it.isNeeded() }.forEach {
            debug("Apply strategy ${it.name}")
            it.play(turn)
        }

        mainStrategy.play(turn)

        waitForLife()
        println()
    }
}