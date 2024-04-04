import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.json.JSONObject

data class User(val id: Int, var name: String)

data class HandlerSpec(val method: Regex, val path: Regex, val handler: (String, Map<String, String>, InputStream, PrintWriter) -> Unit)

val users = mutableListOf(
    User(1, "John"),
    User(2, "Alice"),
    User(3, "Bob"),
    User(4, "Emily")
)

var body: String = ""

fun main(args: Array<String>) {
    val server = ServerSocket(4444)


    while (true) {
        val socket = server.accept()

        thread { server(socket) }
    }
}

fun handleGetAllUsers(path: String, headers: Map<String, String>, input: InputStream, output: PrintWriter) {
    output.println("HTTP/1.1 200 OK")
    output.println("Content-Type: application/json; charset=utf-8")
    output.println()
    for ((index, user) in users.withIndex()) {
        if (index != 0) {
            output.println(",")
        }
        output.println("  {\"id\": ${user.id}, \"name\": \"${user.name}\"}")
    }
}

fun handleGetUserById(path: String, headers: Map<String, String>, input: InputStream, output: PrintWriter) {
    val id = checkNotNull(extractId(path))

    val user = checkNotNull(findUserById(id))

    output.println("HTTP/1.1 200 OK")
    output.println("Content-Type: application/json; charset=utf-8")
    output.println()
    output.println("{\"id\": ${user.id}, \"name\": \"${user.name}\"}")
}

fun handleDeleteUserById(path: String, headers: Map<String, String>, input: InputStream, output: PrintWriter) {
    val id = checkNotNull(extractId(path))

    val user = checkNotNull(deleteUserById(id))

    output.println("HTTP/1.1 200 OK")
    output.println("Content-Type: application/json; charset=utf-8")
    output.println()
    output.println("{\"id\": ${user.id}, \"name\": \"${user.name}\"}")
}

fun handleAddUser(path: String, headers: Map<String, String>, input: InputStream, output: PrintWriter) {
    val requestBody = body.trim()
    val json = JSONObject(requestBody)

    val id = json.getInt("id")
    val name = json.getString("name")

    addUserById(id, name)

    output.println("HTTP/1.1 201 Created")
    output.println("Content-Type: application/json; charset=utf-8")
    output.println()
    output.println("{\"id\": $id, \"name\": \"$name\"}")
}

fun handleEditUser(path: String, headers: Map<String, String>, input: InputStream, output: PrintWriter) {
    val requestBody = body.trim()
    val json = JSONObject(requestBody)

    val id = json.getInt("id")
    val name = json.getString("name")

    editUserById(id, name)

    output.println("HTTP/1.1 200 OK")
    output.println("Content-Type: application/json; charset=utf-8")
    output.println()
    output.println("{\"id\": $id, \"name\": \"$name\"}")
}

fun server(socket: Socket) {
    val client: Socket = socket

    val output = PrintWriter(client.getOutputStream(), true)
    val inputStream = client.inputStream

    val input = BufferedReader(InputStreamReader(inputStream))

    val requestLine = input.readLine() ?: return
//    println(requestLine)
//    val requestLines = input.readLines()
//    println(requestLines)

    val request = requestLine.split(" ")
    val method = request[0]
    val path = request[1]

    val handlers = listOf(
        HandlerSpec("GET".toRegex(), "/api/users/\\d+".toRegex(),  ::handleGetUserById),
        HandlerSpec("DELETE".toRegex(), "/api/users/\\d+".toRegex(),  ::handleDeleteUserById),
        HandlerSpec("GET".toRegex(), "/api/users/".toRegex(),  ::handleGetAllUsers),
        HandlerSpec("POST".toRegex(), "/api/users/".toRegex(),  ::handleAddUser),
        HandlerSpec("PUT".toRegex(), "/api/users/".toRegex(),  ::handleEditUser),
    )

    val headers = mutableMapOf<String, String>()

    var header: String? = input.readLine()

    while (!header.isNullOrEmpty()) {
        val headerParts = header.split(":")
        val key = headerParts[0]
        val value = headerParts[1]
        headers[key] = value
        header = input.readLine()
    }

    for ((methodRegex, pathRegex, handler) in handlers)
        if (method.matches(methodRegex) && path.matches(pathRegex)) {
            if (method == "POST" || method == "PUT") {
                val contentLength = headers.entries.last()
                val contentLengthValue = contentLength.value.trim().toInt()
                println(contentLengthValue)

                val byteArray = ByteArray(contentLengthValue)

                for (i in byteArray.indices)
                    byteArray[i] = input.read().toByte()

                body = byteArray.toString(Charsets.UTF_8)
            }
            handler(path, headers, inputStream, output)
            client.close()

            return
        }

//    output.println("""
//        HTTP/1.1 404 Not Found
//    """.trimIndent())

//    println(users)

    client.close()
}

fun findUserById(id: Int): User? {
    return users.find { user -> user.id == id }
}

fun addUserById(id: Int, name: String) {
    if (users.any { it.id == id }) {
        println("NOOOOOOOOOOO!!!!!")
    } else {
        users.add(User(id, name))
    }
}

fun editUserById(id: Int, newName: String) {
    val user = users.find { it.id == id }
    if (user != null) {
        user.name = newName
    }
}

fun deleteUserById(id: Int): User? {
    val user = users.find { user -> user.id == id }
    users.remove(user)
    return user
}

fun extractId(path: String): Int? {
    val regex = Regex("/api/users/(\\d+)")
    val matchResult = regex.matchEntire(path)
    return if (matchResult != null) matchResult.groupValues[1].toIntOrNull() else null
}
