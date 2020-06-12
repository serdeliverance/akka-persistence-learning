package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.collection.mutable

/**
  * Snapshot: is a way to telling akka to recover the state of an actor from an specific point in time.
  * It allow us to recover the state fast. Otherwise, we had to read all the event store and re apply all the events
  * to the actor in order tu recover it state.
  *
  * Pattern:
  * - after each persist, maybe save a snapshot (logic is up to you)
  * - if you save a snapshot, handle the SnapshotOffer message in the receivRecover
  * - [Optional but best practice] handle the SaveSnapshotSuccess and SaveSnapshotFailure in receiveCommand
  */
object Snapshots extends App {

  // COMMANDS
  case class ReceiveMessage(contents: String)
  case class SendMessage(contents: String)

  // EVENTS
  case class ReceivedMessageRecord(id: Int, content: String)
  case class SentMessageRecord(id: Int, content: String)

  object Chat {
    def props(owner: String, contact: String) = Props(new Chat(owner,contact))
  }

  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {
    val MAX_MESSAGES = 10

    var commandsWithoutCheckpoint = 0

    var currentMessageId = 0
    val lastMessages = new mutable.Queue[(String, String)]()

    override def persistenceId: String = s"$owner-$contact-chat"

    override def receiveCommand: Receive = {
      case ReceiveMessage(contents) =>
        persist(ReceivedMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Received message: ${contents}")
          maybeReplaceMessage(contact, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case SendMessage(contents) =>
        persist(SentMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Sent message: $contents")
          maybeReplaceMessage(owner, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case "print" =>
        log.info(s"more recent messages: $lastMessages")
      case SaveSnapshotSuccess(metadata) => log.info(s"Save snapshot succeded: $metadata")
      case SaveSnapshotFailure(metadata, reason) => log.info(s"Save snapshot $metadata failed because of $reason")
    }

    override def receiveRecover: Receive = {
      case ReceivedMessageRecord(id, contents) =>
        log.info(s"Recovered received message $id:$contents ")
        maybeReplaceMessage(contact, contents)
        currentMessageId = id
      case SentMessageRecord(id, contents) =>
        log.info(s"Recovered sent message $id:$contents")
        maybeReplaceMessage(owner,contents)
        currentMessageId = id
      case SnapshotOffer(metadata, contents) => // akka special message to recover state from snapshot
        log.info(s"Recovered snapshot: $metadata")
        contents.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    def maybeReplaceMessage(sender: String, contents: String): Unit = {
      if (lastMessages.size >= MAX_MESSAGES) {
        lastMessages.dequeue()
      }
      lastMessages.enqueue((sender, contents))
    }

    def maybeCheckpoint(): Unit = {
      commandsWithoutCheckpoint += 1

      if (commandsWithoutCheckpoint >= MAX_MESSAGES) {
        log.info("Saving snapshot...")
        saveSnapshot(lastMessages) // persist any data serializable data to the datastore... it is an asynchronous operation
        commandsWithoutCheckpoint = 0
      }
    }
  }

  val system = ActorSystem("SnapshotsDemo")
  val chat = system.actorOf(Chat.props("sergio123", "richard123"))

//  for (i<-1 to 1000) {
//    chat ! ReceiveMessage(s"Akka Rocks $i")
//    chat ! SendMessage(s"Akka Rules $i")
//  }

  chat ! "print"

  // recover the state after that could take a while. In cases like that, use snapshots
}
