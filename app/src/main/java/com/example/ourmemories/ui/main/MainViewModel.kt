package com.example.ourmemories.ui.main

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ourmemories.data.models.User
import com.example.ourmemories.R
import com.example.ourmemories.data.repositories.MainRepository
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * ViewModel для главного экрана [MainFragment].
 */
class MainViewModel(
    application: Application, private val repository: MainRepository
) : AndroidViewModel(application) {

    private var localBonusProcessedDate: Long = 0

    @SuppressLint("StaticFieldLeak")
    private val context = application.applicationContext

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _isWidgetLoading = MutableLiveData(false)
    val isWidgetLoading: LiveData<Boolean> = _isWidgetLoading

    private val _partnerUser = MutableLiveData<User?>()
    val partnerUser: LiveData<User?> = _partnerUser

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    /**
     * Список доступных эмодзи-статусов.
     */
    val availableStatuses = listOf("😴", "💼", "❤️", "🏠", "🎮", "🍔", "☕", "🎉", "💪", "🎧", "🚗", "📚")


    private var myListener: ListenerRegistration? = null
    private var partnerListener: ListenerRegistration? = null
    private var currentPartnerUid: String? = null

    init {
        startListening()
    }

    fun getStatuses(): List<String> {
        return availableStatuses
    }

    /**
     * Подписка на изменения данных пользователя.
     */
    fun startListening() {
        val myUid = repository.getCurrentUserUid() ?: return

        myListener?.remove()
        myListener = repository.listenToUser(myUid) { user ->
            _currentUser.value = user
            if (user != null) {
                checkDailyBonus(user)
                handlePartnerListener(user.partnerUid)
            }
        }
    }

    /**
     * Подписка на изменения данных партнера.
     */
    private fun handlePartnerListener(partnerUid: String?) {
        if (partnerUid == currentPartnerUid && partnerListener != null) return
        currentPartnerUid = partnerUid
        partnerListener?.remove()

        if (partnerUid != null) {
            partnerListener = repository.listenToUser(partnerUid) { partner ->
                _partnerUser.value = partner
            }
        } else {
            _partnerUser.value = null
        }
    }

    /**
     * Проверка ежедневного бонуса.
     */
    fun checkDailyBonus(user: User) {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (localBonusProcessedDate == today) return

        if (user.lastDailyDate < today) {
            localBonusProcessedDate = today
            val dailyBonus = 10L

            repository.updateTreePoints(user.uid, dailyBonus, today)

            _toastMessage.value = try {
                context.getString(R.string.daily_bonus_toast, dailyBonus)
            } catch (e: Exception) {
                "Bonus: $dailyBonus"
            }
        } else {
            localBonusProcessedDate = today
        }
    }

    /**
     * Отправка приветствия партнеру.
     */
    fun sendHello(partnerUid: String) {
        val myUid = repository.getCurrentUserUid() ?: return
        val myName =
            _currentUser.value?.name ?: context.getString(R.string.your_partner_default_name)

        repository.sendHello(
            myUid,
            myName,
            partnerUid,
            onSuccess = { _toastMessage.value = context.getString(R.string.hello_sent) },
            onFailure = { _toastMessage.value = context.getString(R.string.error_send) }
        )
    }

    /**
     * Обновление статуса виджета.
     */
    fun updateWidgetStatus(hasWidget: Boolean) {
        val uid = repository.getCurrentUserUid() ?: return
        repository.updateWidgetStatus(uid, hasWidget)
    }

    /**
     * Отправка фото партнеру.
     */
    fun sendWidgetPhoto(uri: Uri) {
        val partnerUid = _currentUser.value?.partnerUid
        if (partnerUid == null) {
            _toastMessage.value = context.getString(R.string.no_partner_for_widget)
            return
        }

        val partner = _partnerUser.value
        if (partner == null) {
            _toastMessage.value = context.getString(R.string.partner_data_loading)
            return
        }

        if (partner.hasWidget == false) {
            _toastMessage.value = context.getString(R.string.partner_no_widget_hint)
        }

        _isWidgetLoading.value = true

        repository.uploadWidgetPhoto(partnerUid, uri, onSuccess = {
            _isWidgetLoading.value = false
            _toastMessage.value = context.getString(R.string.widget_photo_sent)
        }, onFailure = { errorMsg ->
            _isWidgetLoading.value = false
            _toastMessage.value = context.getString(R.string.error_upload, errorMsg)
        })
    }

    /**
     * Обновление статуса пользователя.
     */
    fun updateStatus(status: String?) {
        val uid = repository.getCurrentUserUid() ?: return
        repository.updateStatus(uid, status) {
            _toastMessage.value = context.getString(R.string.error_status_update)
        }
    }

    /**
     * Обновление записки пользователя.
     */
    fun updateSharedNote(text: String) {
        val uid = repository.getCurrentUserUid() ?: return
        val partnerUid = _currentUser.value?.partnerUid

        repository.updateSharedNote(
            uid,
            partnerUid,
            text,
            onSuccess = { _toastMessage.value = context.getString(R.string.note_updated) },
            onFailure = { _toastMessage.value = context.getString(R.string.error_save) })
    }

    /**
     * Сохранение даты начала отношений.
     */
    fun saveRelationshipDate(timestamp: Long) {
        val uid = repository.getCurrentUserUid() ?: return
        val partnerUid = _currentUser.value?.partnerUid
        repository.saveRelationshipDate(uid, partnerUid, timestamp)
    }

    /**
     * Вычисляет количество дней с даты начала отношений.
     */
    fun calculateDays(timestamp: Long): Long {
        if (timestamp == 0L) return 0
        val diff = System.currentTimeMillis() - timestamp
        return TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0)
    }

    /**
     * Обновление последнего времени активности пользователя.
     */
    fun updateLastActive() {
        val uid = repository.getCurrentUserUid() ?: return
        repository.updateLastActive(uid)
    }

    /**
     * Подключение к партнеру.
     */
    fun connectPartner(code: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val myUid = repository.getCurrentUserUid() ?: return
        if (currentPartnerUid != null) {
            onFailure(context.getString(R.string.error_already_has_partner))
            return
        }

        repository.connectPartner(myUid, code, onSuccess) { errorCode ->
            val msgRes = when (errorCode) {
                "CODE_NOT_FOUND" -> R.string.error_code_not_found
                "USER_BUSY" -> R.string.error_already_has_partner
                else -> R.string.error_network
            }
            onFailure(context.getString(msgRes))
        }
    }

    /**
     * Разрыв связи с партнером.
     */
    fun disconnectPartner(partnerUid: String) {
        val myUid = repository.getCurrentUserUid() ?: return
        repository.disconnectPartner(myUid, partnerUid) {
            _toastMessage.value = context.getString(R.string.disconnected)
        }
    }


    fun onToastShown() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        myListener?.remove()
        partnerListener?.remove()
    }
}