package com.futo.platformplayer

//import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.system.measureTimeMillis

class StatePlatformTests {

    /*
    @Test
    fun testPlatformStateGetVideo(){
        runBlocking {
            StatePlatform.instance.updateAvailableClients(InstrumentationRegistry.getInstrumentation().targetContext);
            //StatePlatform.instance.selectClients(YoutubeClient.ID, OdyseeClient.ID);
            var youtubeStreamVideoTask = StatePlatform.instance.getContentDetails("https://www.youtube.com/watch?v=bDgolMkLREA");
            val odyseeStreamVideoTask = StatePlatform.instance.getContentDetails("lbry://ads-and-tracking-is-getting-worse-on#e2d1a7334869dfb531c80823064debbb2e75dac5");
            val odyseeStreamVideoTask2 = StatePlatform.instance.getContentDetails("lbry://ads-and-tracking-is-getting-worse-on#e2d1a7334869dfb531c80823064debbb2e75dac5");

            //Assert batching
            assert(odyseeStreamVideoTask == odyseeStreamVideoTask2);

            val youtubeStreamVideo = youtubeStreamVideoTask.await();
            val odyseeStreamVideo = odyseeStreamVideoTask.await();

            assert(youtubeStreamVideo.id.value == "bDgolMkLREA")
            assert(odyseeStreamVideo.id.value == "bMPjhiYTR1GCPKaSGQkFyiVv1juJaY4PaG")
        }
    }*/

    //TODO: Re-enable once getChannel requests are batched for non-subscribed channels.
    /*
    @Test
    fun testPlatformStateGetChannelVideos(){
        val expectedChannelUrl = "https://www.youtube.com/channel/UCL81YHgzH8tcrFfOJJwlSQw";
        val expectedVideoId = "up2TjMuan6o"
        val expectedVideoName= "bag cat";

        runBlocking {
            var youtubeChannelVideosTask = PlatformState.instance.getChannel(expectedChannelUrl);
            var youtubeChannelVideosTask2 = PlatformState.instance.getChannel(expectedChannelUrl);

            //Assert batching
            assert(youtubeChannelVideosTask == youtubeChannelVideosTask2);

            val youtubeStreamVideo = youtubeChannelVideosTask.await();

            val page1Results = youtubeStreamVideo.videos.getResults();
            assert(page1Results.size > 0);
            assert(page1Results.any { it.id.value == expectedVideoId });
        }
    }

    @Test
    fun testPlatformStateSubscription(){
        runBlocking {
            StatePlatform.instance.updateAvailableClients(InstrumentationRegistry.getInstrumentation().targetContext);
            //StatePlatform.instance.selectClients(YoutubeClient.ID, OdyseeClient.ID);
        }
        val expectedChannelUrl = "https://www.youtube.com/channel/UCL81YHgzH8tcrFfOJJwlSQw";
        val expectedVideoId = "up2TjMuan6o"
        val expectedVideoName= "bag cat";

        val channel = runBlocking { StatePlatform.instance.getChannel(expectedChannelUrl).await() };

        val timeExplicit = measureTimeMillis {
            runBlocking {
                val stateExplicit = StateSubscriptions();
                stateExplicit.addSubscription(channel);
                val channel = stateExplicit.getSubscription(expectedChannelUrl);

                assert(channel != null);
            }
        };
        System.out.println("Explicit Subscription update $timeExplicit ms");
    }*/
}