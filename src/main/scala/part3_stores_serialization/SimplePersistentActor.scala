package part3_stores_serialization

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

class SimplePersistentActor extends PersistentActor with ActorLogging {

  // mutable state
  var nMessages = 0

  override def persistenceId: String = "single-persistent-actor"

  override def receiveCommand: Receive = {
    case "print" => log.info(s"I have persisted: $nMessages so far")
    case "snap" => saveSnapshot(nMessages)
    case message => persist(message) { _ =>
      log.info(s"Persisting $message")
      nMessages += 1
    }
    case SaveSnapshotSuccess(metadata) => log.info(s"Save snapshot was sucessful: $metadata")
    case SaveSnapshotFailure(_, cause) => log.warning(s"Save snapshot failed: $cause")
  }

  override def receiveRecover: Receive = {
    case RecoveryCompleted => log.info("Recovery done")
    case SnapshotOffer(_, payload: Int) =>
      log.info(s"Recovered snapshot: $payload")
      nMessages = payload
    case message =>
      log.info(s"Recovered: $message")
      nMessages += 1
  }
}
