package ca.arnah.runelite.plugin;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.inject.Inject;
import ca.arnah.runelite.RuneLiteHijackProperties;
import com.google.common.hash.Hashing;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;

@Slf4j
public class ArnahPluginClient{
	
	private final OkHttpClient okHttpClient;
	private final Type type = new TypeToken<List<ArnahPluginManifest>>(){}.getType();
	private final Type tempType = new TypeToken<List<MemePluginManifest>>(){}.getType();
	
	@Inject
	private ArnahPluginClient(OkHttpClient okHttpClient){
		this.okHttpClient = okHttpClient;
	}
	
	public List<ArnahPluginManifest> downloadManifest() throws IOException{
		List<ArnahPluginManifest> manifests = new ArrayList<>();
		List<HttpUrl> pluginHubs = RuneLiteHijackProperties.getPluginHubBase();
		for(HttpUrl url : pluginHubs){

			HttpUrl.Builder urlBuilder = new HttpUrl.Builder()
					.scheme(url.scheme())
					.host(url.host());

			List<String> pathSegments = new ArrayList<>(url.pathSegments());

			// Remove empty segment
			pathSegments.remove(pathSegments.size() - 1);
			
			log.info("Fetching latest commit hash...");
			log.info("user: " + pathSegments.get(pathSegments.size() - 3) + " repo: " + pathSegments.get(pathSegments.size() - 2) + " branch: " + pathSegments.get(pathSegments.size() - 1));
			String latestCommitHash = getLatestCommitHash(pathSegments.get(pathSegments.size() - 3), pathSegments.get(pathSegments.size() - 2), pathSegments.get(pathSegments.size() - 1));
			log.info("latestCommitHash: " + latestCommitHash);
			
			// Remove the last segment ("dev" or "main" or whatever the branch is called)
			pathSegments.remove(pathSegments.size() - 1);

			for (String segment : pathSegments) {
				urlBuilder.addPathSegment(segment);
			}

			urlBuilder.addPathSegment(latestCommitHash);
			HttpUrl newUrl = urlBuilder.build();

			urlBuilder.addPathSegments("plugins.json").addQueryParameter("v", String.valueOf(1));
			HttpUrl manifest = urlBuilder.build();

			log.info("Downloading manifest...");
			log.info("manifest url: " + manifest.url());
			
			
			try(Response res = okHttpClient.newCall(new Request.Builder().url(manifest).build()).execute()){
				if(res.code() != 200){
					throw new IOException("Non-OK response code: " + res.code() + " on url " + manifest);
				}
				
				BufferedSource src = res.body().source();
				
				String data = new String(src.readByteArray(), StandardCharsets.UTF_8);
				
				List<ArnahPluginManifest> newManifests = RuneLiteAPI.GSON.fromJson(data, type);
				
//				if(data.contains("releases")){
//					List<MemePluginManifest> memeManifests = RuneLiteAPI.GSON.fromJson(data, tempType);
//					newManifests.stream().filter(m->m.getUrl() == null).forEach(m->{
//						MemePluginManifest.MemeRelease release = memeManifests.stream()
//							.filter(mm->m.getInternalName().equals(mm.getInternalName()))
//							.map(mm->mm.getReleases().get(mm.getReleases().size() - 1))
//							.findFirst()
//							.orElse(null);
//						if(release == null) return;
//						m.setUrl(release.getUrl());
//						m.setHash(release.getSha512sum().toLowerCase());
//						m.setHashType(Hashing::sha512);
//					});
//				}
				
//				newManifests.stream().filter(m->m.getUrl() == null).forEach(m->m.setUrl(url + "/" + m.getProvider() + "/" + m.getInternalName() + ".jar"));

				newManifests.stream().filter(m->m.getUrl() == null).forEach(m-> {
					log.info("testestestestest: " + newUrl);
					
					m.setUrl(newUrl + "/" + m.getProvider() + "/" + m.getInternalName() + ".jar");
					log.info(m.getUrl());
				});
				
				manifests.addAll(newManifests);
			}catch(Exception ex){
				if(ex instanceof IOException && pluginHubs.size() != 1){
					log.error("", ex);
				}else{
					throw ex;
				}
			}
		}
		return manifests;
	}

	public String getLatestCommitHash(String user, String repo, String branchName) throws IOException {
		OkHttpClient client = new OkHttpClient();
		HttpUrl url = new HttpUrl.Builder()
				.scheme("https")
				.host("api.github.com")
				.addPathSegment("repos")
				.addPathSegment(user)
				.addPathSegment(repo)
				.addPathSegment("branches")
				.addPathSegment(branchName)
				.build();

		Request request = new Request.Builder()
				.url(url)
				.build();

		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

			String jsonData = response.body().string();
//			JsonObject jsonObject = new JsonParser().parse(jsonData).getAsJsonObject();
//			String latestCommitHash = jsonObject.getAsJsonObject("commit").getAsJsonObject("sha").getAsString();
			JsonObject jsonObject = RuneLiteAPI.GSON.fromJson(jsonData, JsonObject.class);
//			String latestCommitHash = jsonObject.getAsJsonObject("commit").getAsJsonObject("sha").getAsString();
			String latestCommitHash = jsonObject.getAsJsonObject("commit").get("sha").getAsString();
			
			return latestCommitHash;
		}
	}
	
	@Getter
	private static class MemePluginManifest{
		
		@SerializedName(value = "internalName", alternate = {"id"})
		private String internalName;
		
		private List<MemeRelease> releases;
		
		@Getter
		private static class MemeRelease{
			
			private String url;
			private String sha512sum;
		}
	}
}