package com.example.findme.ui.screen.account

import com.example.findme.data.model.AccountProfile
import com.example.findme.data.model.QuickMessage

/** UI actions kept separate so the account screens can be previewed without a live session. */
interface AccountActions {
    fun refresh() {}
    fun login(username: String, password: String) {}
    fun logout() {}
    fun saveProfile(name: String, username: String) {}
    fun saveSchedule(existingId: String?, day: Int, start: String, end: String, title: String, location: String, category: String, modality: String) {}
    fun deleteSchedule(id: String) {}
    fun setStatus(text: String, minutes: Long) {}
    fun clearStatus() {}
    fun invite() {}
    fun accept(code: String) {}
    fun disconnect(id: String) {}
    fun selectPerson(profile: AccountProfile) {}
    fun saveMessage(label: String, message: String) {}
    fun deleteMessage(id: String) {}
    fun configureWidget(message: QuickMessage) {}
    fun send(message: QuickMessage) {}
}
