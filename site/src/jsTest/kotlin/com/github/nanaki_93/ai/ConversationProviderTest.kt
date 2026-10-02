package com.github.nanaki_93.ai

import com.github.nanaki_93.content.ContentCodec
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.*
import kotlin.test.*

class ConversationProviderTest {
    private fun lesson(): com.github.nanaki_93.content.Lesson {
        val fs = js("require('fs')"); val path = js("require('path')"); var root = js("process.cwd()") as String
        while (!(fs.existsSync(path.join(root, "PLAN.md")) as Boolean)) root = path.dirname(root) as String
        return ContentCodec.decodeLesson(fs.readFileSync(path.join(root,"site/src/jsMain/resources/public/content/lessons/confirm-meeting-time.json"),"utf8") as String)
    }
    private val valid = """{"done":true,"message":{"role":"assistant","content":"{\"reply\":\"はい、三時です。\",\"suggestion\":\"One possible phrasing.\"}"}}"""
    @Test fun endpointsModelsAndStructuredRepliesAreBounded() {
        for (endpoint in listOf("https://cloud.test", "http://127.0.0.1.evil.test", "http://user@localhost", "http://localhost:0", "http://localhost/other", "http://localhost?target=x"))
            assertFailsWith<IllegalArgumentException> { validateLocalEndpoint(endpoint) }
        assertEquals("http://127.0.0.1:8765/ollama",validateLocalEndpoint("http://127.0.0.1:8765/ollama/"))
        assertFalse(localModelName("remote:cloud")); assertTrue(localModelName("local:small"))
        assertTrue(decodeModelReply(valid).generated)
        assertFails { decodeModelReply(valid.replace("\"done\":true", "\"done\":false")) }
        assertFails { decodeModelReply("<script>bad</script>") }
        assertFails { decodeModelReply(valid.replace("One possible phrasing.","a".repeat(1300))) }
    }
    @Test fun authoredDefaultAndLocalRequestNeverWriteProgress() = runTest {
        val lesson = lesson()
        assertFalse(AuthoredConversationProvider.reply(lesson, emptyList(), "anything").generated)
        val requests = mutableListOf<String>()
        val transport = object : LocalModelTransport {
            override suspend fun request(endpoint:String,path:String,body:String?):String {
                requests += path
                if(path=="/api/tags") return """{"models":[{"name":"local:small","size":123},{"name":"remote:cloud","size":0}]}"""
                val request = Json.parseToJsonElement(body!!).jsonObject
                assertEquals(false,request["stream"]!!.jsonPrimitive.boolean)
                assertTrue(request["messages"].toString().contains(lesson.communicationGoal))
                return valid
            }
        }
        val provider=OllamaConversationProvider("http://localhost:11434","local:small",transport)
        assertEquals(1,provider.installed().size)
        assertEquals("はい、三時です。",provider.reply(lesson,emptyList(),"三時ですね。").reply)
        assertFailsWith<IllegalArgumentException> { provider.reply(lesson,List(6){ConversationTurn("a","b")},"next") }
        assertTrue(requests.all { it=="/api/tags" || it=="/api/chat" })
    }
    @Test fun requestTimeoutCancelsFakeTransport() = runTest {
        var cancelled = false
        val transport = object : LocalModelTransport {
            override suspend fun request(endpoint:String,path:String,body:String?):String {
                try { delay(60_000); return "{}" } finally { cancelled = true }
            }
        }
        assertFailsWith<TimeoutCancellationException> { installedModels("http://localhost:11434",transport) }
        assertTrue(cancelled)
    }
}
