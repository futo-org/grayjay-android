package com.futo.platformplayer.states

import android.content.Context
import kotlin.streams.asSequence
import kotlin.streams.toList

/***
 * Used to read assets
 */
class StateAssets {
    companion object {
        private val _cache: HashMap<String, String?> = HashMap();

        private fun resolvePath(base: String, path: String, maxParent: Int = 1): String {
            var parentAllowance = maxParent;
            var toSkip = 0;
            val parts1 = base.split('/').toMutableList();
            val parts2 = path.split('/').toMutableList();

            for(part in parts2) {
                if(part == "." || part == "..") {
                    if(parentAllowance <= 0)
                        throw IllegalStateException("Path [${path}] attempted to escape path..");
                    parts1.removeLast();
                    toSkip++;
                }
                else
                    break;
            }
            return (parts1 + parts2.stream().skip(toSkip.toLong()).asSequence().toList()).joinToString("/");
        }

        /**
         * Does basic asset resolving under certain conditions
         */
        fun readAssetRelative(context: Context, base: String, path: String, cache: Boolean = false) : String? {
            val finalPath = resolvePath(base, path);
            return readAsset(context, finalPath, cache);
        }
        fun readAssetBinRelative(context: Context, base: String, path: String) : ByteArray? {
            val finalPath = resolvePath(base, path);
            return readAssetBin(context, finalPath);
        }

        fun readAsset(context: Context, path: String, cache: Boolean = false) : String? {
            var text: String? = null;
            synchronized(_cache) {
                if (!_cache.containsKey(path)) {
                    text = context
                        ?.assets
                        ?.open(path)
                        ?.bufferedReader()
                        ?.use { it.readText(); };
                    _cache.put(path, text);
                }
                else
                    text = _cache.get(path);
            }
            return text;
        }
        fun readAssetBin(context: Context, path: String) : ByteArray? {
            val str = context.assets?.open(path);
            if(str == null)
                return null;
            else return str.use { it.readBytes() };
        }
    }
}