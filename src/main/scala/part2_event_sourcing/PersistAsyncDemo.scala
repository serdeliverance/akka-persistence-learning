package part2_event_sourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

/**
  * Persist async:
  *   - high throughput use-cases
  *   - relaxed event ordering guarantees
  *
  * persist() is an async operation but... between persist(Event()) and the execution of the callback, there is a
  * time gap in which the persistent actor stages all the commands that receives.
  * persistAsync() allows the persistent actor to continue handling commands during that time gap. That is the difference.
  * persistAsync relaxes the persistent guarantees and allow us to handle more messages.
  *
  * PROS:
  *   - Use persistAsync when you have a high thoughput need
  *
  * TRADEOFFS:
  *   - Don't use it when you are interested on preserving the order of the events.
  *   - You have to be careful when dealing with mutable state, because you can lead with a non-deterministic scenario
  *
  * Use persist() when you need order guarantees.
  */
object PersistAsyncDemo extends App {

  case class Command(contents: String)
  case class Event(contents: String)

  object CritialStreamProcessor {
    def props(eventAggregator: ActorRef) = Props(new CritialStreamProcessor(eventAggregator))
  }

  class CritialStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {
    override def persistenceId: String = "criticial-stream-processor"

    override def receiveCommand: Receive = {
      case Command(contents) =>
        eventAggregator ! s"Persisting $contents"
        persistAsync(Event(contents)) { e =>
          eventAggregator ! e
        }

        // some actual computation
        val processContents = contents + "_processed"
        persistAsync(Event(processContents)) { e =>
          eventAggregator ! e
        }
    }

    override def receiveRecover: Receive = {
      case message => log.info(s"Received: $message")
    }
  }


  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Aggregating $message")
    }
  }

  val system = ActorSystem("PersistAsyncDemo")
  val eventAggregator = system.actorOf(Props[EventAggregator], "eventAggregator")
  val streamProcessor = system.actorOf(CritialStreamProcessor.props(eventAggregator), "streamProcessor")

  streamProcessor ! Command("command1")
  streamProcessor ! Command("command2")
}
