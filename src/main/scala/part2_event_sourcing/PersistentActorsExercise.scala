package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import scala.collection.mutable

object PersistentActorsExercise extends App {

  /*
    Persistent actor for a voting station
    Keep:
      - the citizen who voted
      - the poll: mapping between a candidate and the number of received votes so far

      the actor must be able to recover it's state
   */

  // Commands
  case class Vote(citizenPID: String, candidate: String)
  case object Shutdown

  // Events
  case class VoteRecorded(citizenPID: String, candidate: String)

  class VotingStation extends PersistentActor with ActorLogging {

    var citizendVotes: Map[String, String] = Map()
    var citizens: mutable.Set[String] = new mutable.HashSet[String]()

    var poll: Map[String, Int] = Map()

    override def persistenceId = "vote-station"

    override def receiveCommand: Receive = {
      case Vote(citizenPID, candidate) =>
        if (!citizens.contains(citizenPID)) {
          log.info(s"Receiving vote from citizen: $citizenPID to candidate: $candidate")
          persist(VoteRecorded(citizenPID, candidate)) { e =>
            handleInternalStateChange(citizenPID, candidate)
          }
        }
      case "print" => log.info(s"current state: $poll")
      case Shutdown => context.stop(self)
    }

    override def receiveRecover: Receive = {
      case vote @ VoteRecorded(citizenPID, candidate) =>
        log.info(s"Recovered: $vote")
        handleInternalStateChange(citizenPID, candidate)
    }

    def handleInternalStateChange(citizenPID: String, candidate: String): Unit = {
      citizens.add(citizenPID)
      val newVotesCount = poll.getOrElse(candidate, 0) + 1
      poll = poll + (candidate -> newVotesCount)
      log.info(s"citizen: $citizenPID votes for candidate: $candidate and it has $newVotesCount votes")
    }
  }

  val system = ActorSystem("persistentActorExercise")
  val voteStation = system.actorOf(Props[VotingStation], "voteStation")

  val votesMap = Map[String, String](
    "john" -> "alberto",
    "tim" -> "macri",
    "alice" -> "alberto",
    "susan" -> "alberto",
    "juan" -> "menem"
  )

  votesMap.keys.foreach { citizen =>
    voteStation ! Vote(citizen, votesMap(citizen))
  }

  voteStation ! "print"
}
