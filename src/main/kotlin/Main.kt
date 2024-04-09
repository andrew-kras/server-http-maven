import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.json.JSONObject

data class User(val id: Int, var name: String)

data class HandlerSpec(val method: Regex, val path: Regex, val handler: (String, String, Map<String, String>, InputStream, PrintWriter) -> Unit)

val users = mutableListOf(
    User(1, "John"),
    User(2, "Alice"),
    User(3, "Bob"),
    User(4, "Emily")
)

fun main(args: Array<String>) {
    val server = ServerSocket(4444)


    while (true) {
        val socket = server.accept()

        thread { server(socket) }
    }
}

fun handleGetAllUsers(body: String, path: String, headers: Map<String, String>, input: InputStream, output: PrintWriter) {
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

fun handleGetUserById(body: String, path: String, headers: Map<String, String>, input: InputStream, output: PrintWriter) {
    val id = checkNotNull(userExtractId(path))

    val user = checkNotNull(findUserById(id))

    output.println("HTTP/1.1 200 OK")
    output.println("Content-Type: application/json; charset=utf-8")
    output.println()
    output.println("{\"id\": ${user.id}, \"name\": \"${user.name}\"}")
}

fun handleDeleteUserById(body: String, path: String, headers: Map<String, String>, input: InputStream, output: PrintWriter) {
    val id = checkNotNull(userExtractId(path))

    val user = checkNotNull(deleteUserById(id))

    output.println("HTTP/1.1 200 OK")
    output.println("Content-Type: application/json; charset=utf-8")
    output.println()
    output.println("{\"id\": ${user.id}, \"name\": \"${user.name}\"}")
}

fun handleAddUser(body: String, path: String, headers: Map<String, String>, input: InputStream, output: PrintWriter) {
    val requestBody = body.trim()
    val json = JSONObject(requestBody)

    val name = json.getString("name")

    addUserById(name)

    output.println("HTTP/1.1 201 Created")
    output.println("Content-Type: application/json; charset=utf-8")
    output.println()
    output.println("{\"name\": \"$name\"}")
}

fun handleEditUser(body: String, path: String, headers: Map<String, String>, input: InputStream, output: PrintWriter) {
    val id = checkNotNull(userExtractId(path))

    val requestBody = body.trim()
    val json = JSONObject(requestBody)

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

    val request = requestLine.split(" ")
    val method = request[0]
    val path = request[1]

    val handlers = listOf(
        HandlerSpec("GET".toRegex(), "/api/users/\\d+".toRegex(),  ::handleGetUserById),
        HandlerSpec("DELETE".toRegex(), "/api/users/\\d+".toRegex(),  ::handleDeleteUserById),
        HandlerSpec("GET".toRegex(), "/api/users/".toRegex(),  ::handleGetAllUsers),
        HandlerSpec("POST".toRegex(), "/api/users/".toRegex(),  ::handleAddUser),
        HandlerSpec("PUT".toRegex(), "/api/users/\\d+".toRegex(),  ::handleEditUser),
    )

    val headers = mutableMapOf<String, String>()

    var header: String? = input.readLine()

    while (!header.isNullOrEmpty()) {
        val headerParts = header.split(":")
        val key = headerParts[0]
        val value = headerParts[1]
        headers[key] = value
        header = input.readLine().lowercase()
    }

    for ((methodRegex, pathRegex, handler) in handlers)
        if (method.matches(methodRegex) && path.matches(pathRegex)) {
            if ("content-length" in headers) {
                val contentLength = headers["content-length"]
                val contentLengthValue = contentLength!!.trim().toInt()
                println(contentLengthValue)

                val byteArray = ByteArray(contentLengthValue)

                for (i in byteArray.indices)
                    byteArray[i] = input.read().toByte()

                val requestBody = byteArray.toString(Charsets.UTF_8)

                handler(requestBody, path, headers, inputStream, output)
            } else {
                val requestBody = ""

                handler(requestBody, path, headers, inputStream, output)
            }
            client.close()

            return
        }

//    output.println("""
//        HTTP/1.1 404 Not Found
//    """.trimIndent())

    client.close()
}

fun findUserById(id: Int): User? {
    return users.find { user -> user.id == id }
}

fun addUserById(name: String) {
    val nextId = users.last().id + 1
    if (users.any { it.id == nextId }) {
        println("NOOOOOOOOOOO!!!!!")
    } else {
        users.add(User(nextId, name))
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

fun userExtractId(path: String): Int? {
    val regex = Regex("/api/users/(\\d+)")
    val matchResult = regex.matchEntire(path)
    return if (matchResult != null) matchResult.groupValues[1].toIntOrNull() else null
}
