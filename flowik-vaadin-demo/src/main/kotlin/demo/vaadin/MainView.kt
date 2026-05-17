package demo.vaadin

import com.vaadin.flow.component.html.H1
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route

@Route("hello")
@PageTitle("Hello, Vaadin!")
class MainView : VerticalLayout() {
    init {
        add(H1("Hello, Vaadin!"))
    }
}