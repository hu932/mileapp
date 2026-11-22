package com.app.helper.ui.login

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.app.helper.databinding.ActivityLoginBinding
import com.app.helper.network.ApiService
import com.app.helper.ui.main.MainActivity
import com.app.helper.util.FingerprintManager
import com.app.helper.util.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (TokenManager.isLoggedIn()) {
            startMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshModeBadge()

        binding.btnLogin.setOnClickListener {
            val accountId = binding.etUsername.text.toString().trim()

            if (accountId.isEmpty()) {
                Toast.makeText(this, "请输入账号 / 下发id", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performLogin(accountId)
        }
    }

    // 登录页顶部模式徽标：每 3 秒实时检测后台库存模式开关
    private fun refreshModeBadge() {
        setModeBadge("detecting")
        lifecycleScope.launch {
            while (isActive) {
                val inventory = try {
                    ApiService.refreshInventoryMode()
                } catch (_: Exception) {
                    false
                }
                setModeBadge(if (inventory) "inventory" else "normal")
                delay(3000)
            }
        }
    }

    private fun setModeBadge(state: String) {
        val tv = binding.tvMode
        when (state) {
            "inventory" -> {
                tv.text = "⚡ 库存模式"
                tv.setTextColor(Color.parseColor("#FFD54F"))
            }
            "normal" -> {
                tv.text = "✓ 普通模式"
                tv.setTextColor(Color.parseColor("#A5D6A7"))
            }
            else -> {
                tv.text = "正在检测后台模式..."
                tv.setTextColor(Color.parseColor("#C5CFFF"))
            }
        }
    }

    private fun performLogin(accountId: String) {
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "登录中..."

        lifecycleScope.launch {
            try {
                val response = ApiService.login(accountId)
                if (response.isSuccess && response.data != null) {
                    TokenManager.saveToken(response.data.token)
                    TokenManager.saveUsername(response.data.accountId.ifBlank { accountId })
                    TokenManager.saveGroupId(response.data.groupId)
                    TokenManager.saveApiType(TokenManager.API_TYPE_1)
                    TokenManager.saveAccountCount(
                        if (response.data.api1TaskAccountCount > 0) response.data.api1TaskAccountCount else response.data.taskAccountCount
                    )
                    Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                    // 每个下发ID 登录后自动换一份新画像：
                    // 同一个指纹反复注册，虾皮看到的是同一台设备，注册到第 5 个左右就会异常。
                    autoRotateFingerprint()
                    startMain()
                    return@launch
                } else {
                    Toast.makeText(this@LoginActivity, response.msg ?: "登录失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "网络错误：${e.message}", Toast.LENGTH_SHORT).show()
            }

            binding.btnLogin.isEnabled = true
            binding.btnLogin.text = "登 录"
        }
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * 登录后自动向后台申请一份新指纹并设为激活。
     *
     * 这一步是「注册别满 5 个就异常」的关键：
     * 引擎按指纹里的 androidId 选机型，指纹不变=同一台设备，
     * 同一设备连续注册几个号就会被判异常。每个下发ID 换一份就不会撞。
     *
     * 失败不影响登录（比如后台当日额度用完），只提示一句。
     */
    private fun autoRotateFingerprint() {
        Thread {
            val fp = FingerprintManager.generateFullFp(this) ?: run {
                val err = FingerprintManager.lastError
                runOnUiThread {
                    Toast.makeText(this, "画像未更新：$err", Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            val name = fp.optString("name", "fp_${System.currentTimeMillis()}")
            FingerprintManager.saveFingerprintData(this, name, fp.toString())
            FingerprintManager.setActiveFp(this, name)
            android.util.Log.i("LoginActivity", "指纹已自动更换: $name")
            runOnUiThread {
                Toast.makeText(this, "画像已更新：$name", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
