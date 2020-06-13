package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

object RecoveryDemo extends App {

  case class Command(contents: String)
  case class Event(id: Int, contents: String)

  class RecoveryActor extends PersistentActor with ActorLogging {

    var latestPersistedEventId = 0

    override def persistenceId: String = "recovery-actor"


    override def receiveCommand: Receive = online(0)

    // for using context.become to have an stateless persistent actor
    def online(latestPersistedEventId: Int): Receive = {
      case Command(contents) =>
        persist(Event(latestPersistedEventId, contents)) { event =>
          log.info(s"Successfully persisted $event")
          context.become(online(latestPersistedEventId + 1))
        }
    }

    override def receiveRecover: Receive = {
      case RecoveryCompleted =>
        log.info("I've just finished recovering")
      case Event(id, contents) =>
//        if (contents.contains("314"))
//          throw new RuntimeException("I can't take this anymore!")
        log.info(s"Recovered: $contents recovery is ${if (this.recoveryFinished) "" else "NOT"} finished.")
        context.become(online(id + 1))
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.info("I failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

    // 3. Customizing recovery... ex: just recover 100 events (it is not a BEST PRACTICE)
    // override def recovery: Recovery = Recovery(toSequenceNr = 100)
    // override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
    override def recovery: Recovery = Recovery.none
  }

  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor], "receoveryActor")

  for (i <- 1 to 1000) {
    recoveryActor ! Command(s"command $i")
  }
  // ALL COMMANDS SENT DURING RECOVERY ARE STASHED

  /*
    2. failure during recovery
      - onRecoveryFailure + the actor is STOPPED.     // because the actor it not be trusted

    3. Customizing recovery

    4. Recovery status or KNOWING when you're done recovering => RecoveryCompleted is useful. Use that in receiveRecover

    5. Stateless actors => solution: context become

      We can use context.become as usual for handling mutable state and having statless actor. However, it makes no sense
      to use it in receiveRecover because this handler never change (you can use context.become, but it will have not effect)
   */
}
