package save

import save.CaseType.*
import java.util.*


fun debug(value: Any?) = System.err.println(value)


data class Point(val x: Int, val y: Int) {
    fun distanceTo(other: Point) = Math.abs(this.x - other.x) + Math.abs(this.y - other.y)
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

data class Case(val position: Point, val type: CaseType) {
    fun adjacentCase(game: Game): List<Case> {
        return listOf(this.position.down, this.position.up, this.position.left, this.position.right)
            .filter { it.x in 0..11 && it.y in 0..11 }
            .map { game.get(it) }
    }
}

data class Player(val units: MutableList<Unit> = mutableListOf(), var gold: Int = 0, var income: Int = 0)

data class Unit(val id: Int, var level: Int = 1, var position: Point, var isReady: Boolean = false)

data class Building(val position: Point, val type: BuildingType, val owner: Player)

data class MineSpot(val position: Point)

data class Game(val myPlayer: Player = Player(), val enemy: Player = Player(), var cases: List<List<Case>> = listOf(), var buildings: List<Building> = listOf(), val mineSpots: List<MineSpot>) {
    fun getOrNull(point: Point): Case? = cases.getOrNull(point.y)?.getOrNull(point.x)
    fun getOrNull(x: Int, y: Int): Case? = cases.getOrNull(y)?.getOrNull(x)
    fun get(point: Point): Case = cases[point.y][point.x]
    fun get(building: Building): Case = cases[building.position.y][building.position.x]

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
                System.err.print(line[x])
                Case(Point(x, y), caseType)
            }
        }
        System.err.println()

        this.buildings = (0 until input.nextInt()).map {
            val ownerInt = input.nextInt()
            debug("owner int: $ownerInt")
            val owner = if (ownerInt == 0) myPlayer else enemy
            val buildingType = when(input.nextInt()) {
                0 -> BuildingType.QG
                1 -> BuildingType.MINE
                2 -> BuildingType.TOWER
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
            } ?: playerOwner.units.add(Unit(unitId, level, position))

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
        return listOf(map.get(position.left), map.get(position.right), map.get(position.down), map.get(position.up)).filter { it.type != VOID }
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

fun Unit.optimoveTo(game: Game, case: Case): Case {
    return PathFinder.findPath(game, this.position, case.position)?.firstOrNull() ?: case
}


fun Unit.findClosestNeutralMine(game: Game): MineSpot? {
    return game.mineSpots.filter { mine -> game.get(mine.position).type == NEUTRAL }.minBy { it.position.distanceTo(this.position) }
}

fun Unit.findClosestNeutralCase(game: Game, enemyQG: Building): Case {
    return game.cases.flatten().filter { it.type == NEUTRAL }.minBy { it.position.distanceTo(this.position) }
        ?: game.get(enemyQG)
}

fun Game.findMineSpotAvailable(): MineSpot? {
    return this.mineSpots.firstOrNull { mine -> this.get(mine.position).type == ALLY_ENABLED && this.buildings.none { it.position == mine.position && it.owner == this.myPlayer } }
}

fun Game.findCaseToTrain(myPlayerUnits: List<Unit>, enemyUnits: List<Unit>, enemyQG: Building): MutableList<Case> {
    return this.cases.asSequence().flatten()
        .filter { case -> case.type == ALLY_ENABLED }
        .map { it.adjacentCase(this) }
        .flatten()
        .filterNot { it.type == VOID && myPlayerUnits.noneIn(it) && enemyUnits.noneIn(it) && this.buildings.noneOn(it)}
        .sortedBy { it.position.distanceTo(enemyQG.position) }
        .toMutableList()
}

fun Game.findCaseToBuild(myPlayerUnits: List<Unit>, myTowers: List<Building>, myPlayerQG: Building): Case? {
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
    debug("Tower case count: ${towerCases.size}")
    return (this.cases.flatten() - towerCases)
        .filter { case -> case.type == ALLY_ENABLED && myPlayerUnits.noneIn(case) && this.buildings.noneOn(case) }
        .sortedBy { it.position.distanceTo(myPlayerQG.position) }
        .firstOrNull()
}

fun List<Unit>.noneIn(case: Case) = this.none { it.position == case.position }
fun List<Building>.noneOn(case: Case) = this.none { it.position == case.position }

fun List<Unit>.attack(attackableUnits: List<Unit>, enemyQG: Building) {
    if (attackableUnits.isNotEmpty()) {
        this.forEach { hunter -> hunter.attack(attackableUnits.minBy { it.position.distanceTo(hunter.position) }!!) }
    } else {
        this.forEach { it.attack(enemyQG) }
    }
}

fun List<Unit>.conquer(mines: List<Building>, enemyQG: Building) {
    if (mines.isNotEmpty()) {
        this.forEach { hunter -> hunter.attack(mines.minBy { it.position.distanceTo(hunter.position) }!!) }
    } else {
        this.forEach { it.attack(enemyQG) }
    }
}

fun Unit.explore(game: Game, enemyQG: Building) {
    val closestNeutralMine = this.findClosestNeutralMine(game)
    if (closestNeutralMine != null) {
        this.move(closestNeutralMine)
    } else {
        this.move(this.findClosestNeutralCase(game, enemyQG))
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
    val myPlayer = game.myPlayer
    val enemy = game.enemy

    var turn = 0

    // game loop
    while (true) {
        game.update(input)
        val playerQG = game.buildings.first { it.owner == myPlayer && it.type == BuildingType.QG }
        val myPlayerMines = game.buildings.filter { it.owner == myPlayer && it.type == BuildingType.MINE }
        val myPlayerTowers = game.buildings.filter { it.owner == myPlayer && it.type == BuildingType.TOWER }
        val enemyQG = game.buildings.first { it.owner == enemy && it.type == BuildingType.QG }
        val enemyMines = game.buildings.filter { it.owner == enemy && it.type == BuildingType.MINE }

        val casesToTrain = game.findCaseToTrain(myPlayer.units, enemy.units, enemyQG)
        val casesToBuildTower = game.findCaseToBuild(myPlayer.units, myPlayerTowers, playerQG)
        val mineToBuild = game.findMineSpotAvailable()

        val unitsLevel1 = myPlayer.units.filter { it.level == 1 && it.isReady }
        val explorer = unitsLevel1.firstOrNull()
        val unitsLevel1Attackers = if (explorer == null) unitsLevel1 else unitsLevel1 - explorer
        val unitsLevel2 = myPlayer.units.filter { it.level == 2 && it.isReady }
        val unitsLevel3 = myPlayer.units.filter { it.level == 3 && it.isReady }

        val enemyUnitsLevel1 = enemy.units.filter { it.level == 1 && it.isReady }
        val enemyUnitsLevel2 = enemy.units.filter { it.level == 2 && it.isReady }
        val enemyUnitsLevel3 = enemy.units.filter { it.level == 3 && it.isReady }

        if (mineToBuild != null && myPlayer.gold >= 20 + myPlayerMines.size * 4) {
            build(mineToBuild)
            myPlayer.gold -= 20 + myPlayerMines.size * 4
        }

        if (casesToBuildTower != null && turn > 10 && myPlayer.gold >= 15 && myPlayer.income >= 5) {
            buildTower(casesToBuildTower)
        }

        if (casesToTrain.isNotEmpty() && myPlayer.gold >= 70 && myPlayer.income >= 35) {
            train(3, casesToTrain.first())
            casesToTrain.remove(casesToTrain.first())
            myPlayer.gold -= 30
            myPlayer.income -= 20
        }

        while (casesToTrain.isNotEmpty() && myPlayer.gold >= 20 && myPlayer.income > 4) {
            train(2, casesToTrain.first())
            casesToTrain.remove(casesToTrain.first())
            myPlayer.gold -= 20
            myPlayer.income -= 4
        }
        while (casesToTrain.isNotEmpty() && myPlayer.gold >= 10) {
            train(1, casesToTrain.first())
            casesToTrain.remove(casesToTrain.first())
            myPlayer.gold -= 10
            myPlayer.income -= 1
        }

        unitsLevel3.attack(enemyUnitsLevel2, enemyQG)
        unitsLevel2.attack(enemyUnitsLevel1, enemyQG)
        unitsLevel1Attackers.conquer(enemyMines, enemyQG)
        explorer?.explore(game, enemyQG)
        debug("My explorer is ${explorer?.id}")


        waitForLife()
        println()

        debug("turn $turn")
        turn++
    }
}