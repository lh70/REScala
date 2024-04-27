package todo

import org.scalajs.dom
import org.scalajs.dom.html.{Div, Input, LI}
import org.scalajs.dom.{HTMLDivElement, KeyboardEvent, UIEvent, document, window}
import rdts.datatypes.contextual.ReplicatedList
import rdts.dotted.Dotted
import rdts.syntax.DeltaBuffer
import reactives.default.*
import reactives.extra.Tags.*
import reactives.structure.Pulse
import scalatags.JsDom
import scalatags.JsDom.all.*
import scalatags.JsDom.tags2.section
import scalatags.JsDom.{Attr, TypedTag}
import todo.Codecs.given
import todo.Todolist.replicaId

import scala.annotation.targetName

class TodoAppUI(val storagePrefix: String) {

  def getContents(): Div = {

    val todoInputTag: Input = input(
      id          := "newtodo",
      `class`     := "new-todo",
      placeholder := "What needs to be done?",
      autofocus   := "autofocus",
      `type`      := "text"
    ).render

    val createTodo = todoInputTag.inputEntered

    val removeAll = Event.fromCallback(button("remove all done todos", onclick := Event.handle))

    val toggleAll = Event.fromCallback {
      input(
        id       := "toggle-all",
        name     := "toggle-all",
        `class`  := "toggle-all",
        `type`   := "checkbox",
        onchange := Event.handle[dom.Event]
      ).render
    }

    val taskrefs = TaskReferences(toggleAll.event, storagePrefix)
    val taskOps  = new TaskOps(taskrefs, replicaId)

    val deltaEvt = GlobalRegistry.subscribe[ReplicatedList[TaskRef]]("tasklist")

    val tasksRDT: Signal[DeltaBuffer[Dotted[ReplicatedList[TaskRef]]]] =
      Storing.storedAs(storagePrefix, DeltaBuffer(Dotted(ReplicatedList.empty[TaskRef]))) { init =>
        Fold(init)(
          taskOps.handleCreateTodo(createTodo),
          taskOps.handleRemoveAll(removeAll.event),
          Fold.branch {
            current.toList.flatMap(_.removed.value).foldLeft(current) { (c, e) => taskOps.handleRemove(c)(e) }
          },
          taskOps.handleDelta(deltaEvt)
        )
      }

    GlobalRegistry.publish("tasklist", tasksRDT)

    val tasksList: Signal[List[TaskRef]] = tasksRDT.map { _.toList }
    val tasksData: Signal[List[TaskData]] =
      Signal.dynamic { tasksList.value.flatMap(l => l.task.value.state.data.read) }
    val taskTags: Signal[List[LI]] = Signal { tasksList.value.map(_.tag) }

    val largeheader = window.location.hash.drop(1)

    val hideEmpty = Signal:
      `style` := (if tasksData.value.isEmpty then "display:none" else "")

    div(
      `class` := "appcontainer",
      div(
        `class` := "todoapp",
        header(
          `class` := "header",
          h1(if (largeheader.nonEmpty) largeheader else "todos"),
          todoInputTag
        ),
        section(
          `class` := "main",
          toggleAll.data,
          label(`for` := "toggle-all", "Mark all as complete"),
          ul(
            `class` := "todo-list",
          ).render.reattach(taskTags)
        ).render.reattach(hideEmpty),
        div(
          `class` := "footer",
          span(
            `class` := "todo-count",
          ).render.reattach(
            Signal {
              val remainingTasks = tasksData.value.count(!_.done)
              println(s"remaining observer")
              List(
                strong(remainingTasks.toString).render,
                span(if (remainingTasks == 1)
                  " item left"
                else " items left").render
              )
            }
          )
        ).render.reattach(hideEmpty)
          .reattach {
            Signal {
              removeAll.data(
                `class` := s"clear-completed${if (!tasksData.value.exists(_.done)) " hidden" else ""}"
              ).render
            }
          }
      )
    ).render
  }

}
