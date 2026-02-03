package io.github.vvb2060.magisk.ui.superuser

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.Process
import androidx.databinding.Bindable
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.viewModelScope
import io.github.vvb2060.magisk.BR
import io.github.vvb2060.magisk.arch.AsyncLoadViewModel
import io.github.vvb2060.magisk.core.AppContext
import io.github.vvb2060.magisk.core.Config
import io.github.vvb2060.magisk.core.Info
import io.github.vvb2060.magisk.core.R
import io.github.vvb2060.magisk.core.utils.RootUtils
import io.github.vvb2060.magisk.core.data.magiskdb.PolicyDao
import io.github.vvb2060.magisk.core.ktx.getLabel
import io.github.vvb2060.magisk.core.model.su.SuPolicy
import io.github.vvb2060.magisk.databinding.MergeObservableList
import io.github.vvb2060.magisk.databinding.RvItem
import io.github.vvb2060.magisk.databinding.bindExtra
import io.github.vvb2060.magisk.databinding.filterList
import io.github.vvb2060.magisk.databinding.set
import io.github.vvb2060.magisk.dialog.SuperuserRevokeDialog
import io.github.vvb2060.magisk.events.AuthEvent
import io.github.vvb2060.magisk.events.SnackbarEvent
import io.github.vvb2060.magisk.utils.asText
import io.github.vvb2060.magisk.view.TextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SuperuserViewModel(
    private val db: PolicyDao
) : AsyncLoadViewModel() {

    private val itemNoData = TextItem(R.string.superuser_policy_none)

    private val itemsHelpers = ObservableArrayList<TextItem>()
    private val itemsPolicies = filterList<PolicyRvItem>(viewModelScope)

    val items = MergeObservableList<RvItem>()
        .insertList(itemsHelpers)
        .insertList(itemsPolicies)
    val extraBindings = bindExtra {
        it.put(BR.listener, this)
        it.put(BR.viewModel, this)
    }

    @get:Bindable
    var loading = false
        private set(value) = set(value, field, { field = it }, BR.loading)

    @SuppressLint("InlinedApi")
    override suspend fun doLoadWork() {
        if (!Info.showSuperUser) {
            return
        }

        loading = true

        // Fetch data in IO thread
        val policyItems = withContext(Dispatchers.IO) {
            db.deleteOutdated()
            db.delete(AppContext.applicationInfo.uid)

            // Fetch all authorization records from database
            val policyMap = db.fetchAll().associateBy { it.uid }
            val pm = AppContext.packageManager

            // Try to get package list via root service first (if available and rooted)
            val packageNames = if (Info.isRooted) {
                try {
                    RootUtils.getInstalledPackages()
                } catch (e: Exception) {
                    // Fall back to standard method if root method fails
                    null
                }
            } else null

            // Get all third-party apps
            val items = if (packageNames != null) {
                // Use root method result - convert package names to ApplicationInfo
                packageNames.asFlow().mapNotNull { packageName ->
                    try {
                        pm.getApplicationInfo(packageName, MATCH_UNINSTALLED_PACKAGES)
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                }
            } else {
                // Fall back to standard method
                pm.getInstalledApplications(MATCH_UNINSTALLED_PACKAGES).asFlow()
            }
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .mapNotNull { appInfo ->
                    try {
                        val packageName = appInfo.packageName
                        val info: android.content.pm.PackageInfo = pm.getPackageInfo(packageName, MATCH_UNINSTALLED_PACKAGES)
                        val applicationInfo = info.applicationInfo ?: return@mapNotNull null

                        // Get existing policy or create new one with QUERY status
                        val policy = policyMap[applicationInfo.uid] ?: SuPolicy(
                            uid = applicationInfo.uid,
                            policy = SuPolicy.QUERY
                        )

                        PolicyRvItem(
                            this@SuperuserViewModel, policy,
                            info.packageName,
                            info.sharedUserId != null,
                            applicationInfo.loadIcon(pm),
                            applicationInfo.getLabel(pm) ?: packageName
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                }.toCollection(ArrayList<PolicyRvItem>())

            // Add shell (UID 2000) if it has a policy or should be shown
            val shellUid = 2000
            if (policyMap.containsKey(shellUid)) {
                val shellPolicy = policyMap[shellUid]!!
                items.add(PolicyRvItem(
                    this@SuperuserViewModel, shellPolicy,
                    "shell",
                    false,
                    pm.defaultActivityIcon,
                    "Shell"
                ))
            }

            // Sort: ALLOW apps first, DENY/QUERY apps after, then by app name
            items.sortWith(compareBy(
                { it.item.policy == SuPolicy.QUERY },  // QUERY last
                { it.item.policy != SuPolicy.ALLOW },  // ALLOW first
                { it.appName.lowercase(Locale.ROOT) },
                { it.packageName }
            ))

            items
        }

        // Update list on main thread
        itemsPolicies.set(policyItems)
        itemsPolicies.filter { true }
        loading = false
    }

    // ---

    fun deletePressed(item: PolicyRvItem) {
        fun updateState() = viewModelScope.launch {
            db.delete(item.item.uid)
            doLoadWork()
        }

        if (Config.suAuth) {
            AuthEvent { updateState() }.publish()
        } else {
            SuperuserRevokeDialog(item.title) { updateState() }.show()
        }
    }

    fun updateNotify(item: PolicyRvItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.update(item.item)
            }
            val res = when {
                item.item.notification -> R.string.su_snack_notif_on
                else -> R.string.su_snack_notif_off
            }
            SnackbarEvent(res.asText(item.appName)).publish()
        }
    }

    fun updateLogging(item: PolicyRvItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.update(item.item)
            }
            val res = when {
                item.item.logging -> R.string.su_snack_log_on
                else -> R.string.su_snack_log_off
            }
            SnackbarEvent(res.asText(item.appName)).publish()
        }
    }

    fun updatePolicy(item: PolicyRvItem, policy: Int) {
        fun updateState() {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (policy >= SuPolicy.ALLOW) {
                            // Grant: update or create policy
                            item.item.policy = policy
                            db.update(item.item)
                        } else {
                            // Deny: delete the policy completely
                            db.delete(item.item.uid)
                        }
                    }
                    // Show snackbar on main thread
                    val res = if (policy >= SuPolicy.ALLOW) {
                        R.string.su_snack_grant.asText(item.appName)
                    } else {
                        R.string.su_snack_deny.asText(item.appName)
                    }
                    SnackbarEvent(res).publish()
                    // Reload to show updated list
                    doLoadWork()
                } catch (e: Exception) {
                    SnackbarEvent("Error: ${e.message}").publish()
                }
            }
        }

        if (Config.suAuth) {
            AuthEvent { updateState() }.publish()
        } else {
            updateState()
        }
    }
}
