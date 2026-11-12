import eu.kanade.tachiyomi.ui.library.*
import eu.kanade.tachiyomi.ui.library.search.*

fun main() {
    val titles = listOf("أَبْطَالُ الساحة", "ابطال الساحة")
    val records = listOf(
        LibraryItem(FakeManga(title=titles[0]),true),
        LibraryItem(FakeManga(title=titles[1]),false),
    )
    check(QueryNode.from("dup:yes title:ابطال").matches(records[0]))
    check(!QueryNode.from("dup:yes title:ابطال").matches(records[1]))
    check(QueryNode.from("dup:no title:ابطال").matches(records[1]))
    check(QueryNode.from("-dup:yes").matches(records[1]))
    check(!QueryNode.from("dup:maybe").matches(records[1]))
    println("SEARCH FILTER EVALUATION: 5 / 5 PASS")
}
