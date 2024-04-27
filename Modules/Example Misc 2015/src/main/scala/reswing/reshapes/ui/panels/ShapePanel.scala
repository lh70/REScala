package reswing.reshapes.ui.panels

import reactives.default.*
import reswing.{ReBoxPanel, ReButton}
import reswing.reshapes.ReShapes
import reswing.reshapes.drawing.{DeleteShape, DrawingSpaceState}
import reswing.reshapes.figures.Shape
import reswing.reshapes.util.ReactiveUtil.UnionEvent

import scala.swing.{BoxPanel, Color, Component, Label, Orientation, ScrollPane}

/** Lists all drawn shapes */
class ShapePanel extends BoxPanel(Orientation.Vertical) {
  def state = ReShapes.drawingSpaceState

  val shapes = Signal.dynamic { if (state.value != null) state.value.shapes.value else List.empty } // #SIG

  val shapeViews = Signal { shapes.value map { shape => new ShapeView(shape, state.value) } } // #SIG

  val shapesPanel = new ReBoxPanel(
    orientation = Orientation.Vertical,
    contents = Signal[Seq[Component]] { // #SIG
      shapeViews.value map { (shapeView: ShapeView) => shapeView: Component }
    }
  )

  contents += new ScrollPane {
    contents = shapesPanel
  }

  val deleted =
    UnionEvent(Signal { shapeViews.value map { shapeView => shapeView.deleted } }) // #SIG //#UE( //#EVT //#IF )
}

class ShapeView(shape: Shape, state: DrawingSpaceState) extends ReBoxPanel(Orientation.Horizontal) {
  val SELECTED_COLOR     = new Color(0, 153, 255)
  val NOT_SELECTED_COLOR = new Color(255, 255, 255)

  val deleteButton = new ReButton("delete")

  val deleted: Event[DeleteShape] = // #EVT
    deleteButton.clicked map { (_: Any) => new DeleteShape(shape) } // #EF

  peer.background = NOT_SELECTED_COLOR
  peer.contents += new Label(shape.toString)
  peer.contents += deleteButton

  mouse.clicks.clicked observe { _ => // #HDL
    state.select.fire(if (state.selectedShape.now != shape) shape else null)
  }

  state.selectedShape.changed observe { selected => // #HDL
    peer.background = if (selected == shape) SELECTED_COLOR else NOT_SELECTED_COLOR
  }
}
