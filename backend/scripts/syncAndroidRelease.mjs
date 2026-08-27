import { syncLatestAndroidRelease } from "../src/services/githubReleaseService.js";

const result = await syncLatestAndroidRelease({
  repository: process.env.GITHUB_REPOSITORY,
  token: process.env.GITHUB_TOKEN,
  apiUrl: process.env.GITHUB_API_URL,
});
console.log(`Synced Android release ${result.versionName} (${result.versionCode}) from ${result.releaseUrl}`);
