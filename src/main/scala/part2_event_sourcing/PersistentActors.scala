import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistentActors extends App {

  // COMMAND
  case class Invoice(recipient: String, date: Date, amount: Int)

  // special messages
  case object Shutdown

  // EVENT
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)
  case class InvoiceBulk(invoices: List[Invoice])

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
      case InvoiceBulk(invoices) =>
        /*
          1) create events
          2) persist all the events
          3) update the actor state when each event is persisted
         */
        val invoiceIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoiceIds).map { pair =>
          val id = pair._2
          val invoice = pair._1

          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }
        persistAll(events) { e =>
          latestInvoiceId += 1
          totalAmount += e.amount
        }
      case Shutdown => context.stop(self)
    }

    override def receiveRecover: Receive = {
      // best practice: follow the logic in the persists steps of receiveCommand
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId = id
        totalAmount += amount
    }

    /*
      This method is called when persisting failed. The actor is stopped

      BEST PRACTICE: start the actor again after a while. Use backoff supervisor
     */
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long) = {
      log.error(s"Fail to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    /*
      Called if the journal fails to persist the event.
      The actor is RESUMED (because the actor state is not corrupted)
    */
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist reject on $event of $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

  for(i<-1 to 10) {
    accountant ! Invoice("The sofa company", new Date, i * 1000)
  }

  val newInvoices = for (i <- 1 to 5) yield Invoice("the awesome chairs", new Date(), i * 2000)
  accountant ! InvoiceBulk(newInvoices.toList)

  /*
    NEVER EVER CALL PERSIST OR PERSISTALL FROM FUTURES.
   */

  /**
    * Shutdown of persistent actors
    *
    * Best Practice: define your own shutdown message
    */
}
