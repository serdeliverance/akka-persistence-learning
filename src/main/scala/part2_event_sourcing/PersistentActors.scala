import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistentActors extends App {

  // COMMAND
  case class Invoice(recipient: String, date: Date, amount: Int)

  // EVENT
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)

  class Accountant extends PersistentActor with ActorLogging {

    var latestInvoiceId = 0
    var totalAmount = 0

    override def persistenceId: String = "simple-account" // best practice: make it unique

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        /*
          when you receive a command
          1) you create an EVENT to persist into the store
          2) you persist the event, the pass in a callback that will get triggered once the event is written
          3) we update the actor's state when the event has persisted
         */
        log.info(s"Receive invoice for amount: $amount")
        persist(InvoiceRecorded(latestInvoiceId, recipient, date, amount)) { e =>
          // is it SAFE to access mutable state (in akka persistence)
          // update mutable state
          latestInvoiceId += 1
          totalAmount += amount
          log.info(s"persisted #{$e.id}, for totalAmount $totalAmount")
        }
    }

    override def receiveRecover: Receive = {
      // best practice: follow the logic in the persists steps of receiveCommand
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId = id
        totalAmount += amount
    }
  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

  for(i<-1 to 10) {
    accountant ! Invoice("The sofa company", new Date, i * 1000)
  }
}
