package com.termoak.app.term

import com.termoak.ffi.SessionAccess
import com.termoak.ffi.SessionParticipant

/** The server sent you away for good (`revoked`, `kicked`, `expired`, `session_ended`, `join_denied`, `forbidden`). */
data class ShareEnd(val code: String, val message: String)

/** In the waiting room until the owner lets you in. */
data class ShareWaiting(val title: String, val owner: String, val participantId: String?)

/**
 * Who is in a shared terminal and who has the keyboard, as this device sees
 * it. A terminal nobody else is in is the default: you own it and can type.
 */
data class LiveShare(
    /** You are the session's owner (always can type and take the keyboard back). */
    val isOwner: Boolean = true,
    /** Your access: `OWNER`, `CONTROL` (can ask for the keyboard) or `VIEW`. */
    val access: SessionAccess = SessionAccess.OWNER,
    /** Your input and resizes reach the terminal now. */
    val canWrite: Boolean = true,
    val participants: List<SessionParticipant> = emptyList(),
    /** Participant with the keyboard (`null`: the owner). */
    val driver: String? = null,
    val driverName: String? = null,
    /** Guest: you asked for the keyboard and wait for the owner. */
    val controlRequested: Boolean = false,
    val waiting: ShareWaiting? = null,
    val ended: ShareEnd? = null,
    /** Owner: people waiting to be let in. */
    val joinRequests: List<SessionParticipant> = emptyList(),
    /** Owner: people asking for the keyboard. */
    val controlRequests: List<SessionParticipant> = emptyList(),
) {
    /** Everyone inside but you. */
    val others: List<SessionParticipant> get() = participants.filter { !it.you && !it.waiting }

    /** The people inside (waiting room excluded). */
    val inside: List<SessionParticipant> get() = participants.filter { !it.waiting }

    val pendingRequests: Int get() = joinRequests.size + controlRequests.size

    /** A guest has the keyboard (not the owner). */
    val guestDriving: Boolean get() = driver != null

    /** Name of who types now, if it is someone else. */
    fun driverLabel(): String? =
        if (driver == null) null else driverName ?: participants.firstOrNull { it.id == driver }?.name

    /** Rebuilds the request lists from a fresh participant list (owner's lists flag them). */
    fun withParticipants(list: List<SessionParticipant>, driver: String?): LiveShare {
        val me = list.firstOrNull { it.you }
        return copy(
            participants = list,
            driver = driver,
            driverName = list.firstOrNull { it.id == driver }?.name ?: if (driver == null) null else driverName,
            controlRequested = if (isOwner) false else me?.requestedControl ?: controlRequested,
            joinRequests = if (isOwner) list.filter { it.waiting } else emptyList(),
            controlRequests = if (isOwner) list.filter { it.requestedControl && !it.you && !it.waiting } else emptyList(),
        )
    }

    fun addJoinRequest(p: SessionParticipant) =
        copy(joinRequests = joinRequests.filterNot { it.id == p.id } + p)

    fun addControlRequest(p: SessionParticipant) =
        copy(controlRequests = controlRequests.filterNot { it.id == p.id } + p)

    fun dropRequest(id: String) = copy(
        joinRequests = joinRequests.filterNot { it.id == id },
        controlRequests = controlRequests.filterNot { it.id == id },
    )
}

/** Something about sharing the user should hear about, even outside the terminal. */
data class ShareNotice(
    val kind: Kind,
    /** The open tab it belongs to, if any. */
    val tabId: String?,
    /** Server session id, if known. */
    val sessionId: String?,
    val title: String,
    /** Who asks, or who shared it. */
    val name: String,
    val participantId: String? = null,
) {
    enum class Kind { JOIN_REQUEST, CONTROL_REQUEST, SHARED_WITH_YOU, CONTROL_GRANTED, CONTROL_REVOKED }
}
