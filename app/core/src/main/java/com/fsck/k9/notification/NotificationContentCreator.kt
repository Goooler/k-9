package com.fsck.k9.notification

import android.text.SpannableStringBuilder
import app.k9mail.core.android.common.contact.ContactRepository
import com.fsck.k9.Account
import com.fsck.k9.K9
import com.fsck.k9.helper.MessageHelper
import com.fsck.k9.mail.Message
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.message.extractors.PreviewResult.PreviewType

internal class NotificationContentCreator(
    private val resourceProvider: NotificationResourceProvider,
    private val contactRepository: ContactRepository,
) {
    fun createFromMessage(account: Account, message: LocalMessage): NotificationContent {
        val (senderName, senderAddress) = getMessageSender(account, message)

        return NotificationContent(
            messageReference = message.makeMessageReference(),
            senderName = getMessageSenderForDisplay(senderName),
            senderAddress = senderAddress,
            subject = getMessageSubject(message),
            preview = getMessagePreview(message),
            summary = buildMessageSummary(senderName, getMessageSubject(message)),
        )
    }

    private fun getMessagePreview(message: LocalMessage): CharSequence {
        val snippet = getPreview(message)
        if (message.subject.isNullOrEmpty() && snippet != null) {
            return snippet
        }

        return SpannableStringBuilder().apply {
            val displaySubject = getMessageSubject(message)
            append(displaySubject)

            if (snippet != null) {
                append('\n')
                append(snippet)
            }
        }
    }

    private fun getPreview(message: LocalMessage): String? {
        val previewType = message.previewType ?: error("previewType == null")
        return when (previewType) {
            PreviewType.NONE, PreviewType.ERROR -> null
            PreviewType.TEXT -> message.preview
            PreviewType.ENCRYPTED -> resourceProvider.previewEncrypted()
        }
    }

    private fun buildMessageSummary(sender: String?, subject: String): CharSequence {
        return if (sender == null) {
            subject
        } else {
            SpannableStringBuilder().apply {
                append(sender)
                append(" ")
                append(subject)
            }
        }
    }

    private fun getMessageSubject(message: Message): String {
        val subject = message.subject.orEmpty()
        return subject.ifEmpty { resourceProvider.noSubject() }
    }

    private fun getMessageSender(account: Account, message: Message): Pair<String?, String> {
        val localContactRepository = if (K9.isShowContactName) contactRepository else null
        var isSelf = false

        val fromAddresses = message.from
        if (!fromAddresses.isNullOrEmpty()) {
            isSelf = account.isAnIdentity(fromAddresses)
            if (!isSelf) {
                val fromAddress = fromAddresses.first()
                val fromName = MessageHelper.toFriendly(fromAddress, localContactRepository).toString()
                return fromName to fromAddress.address
            }
        }

        if (isSelf) {
            // show To: if the message was sent from me
            val recipients = message.getRecipients(Message.RecipientType.TO)
            if (!recipients.isNullOrEmpty()) {
                val recipientAddress = recipients.first()
                val recipientDisplayName = MessageHelper.toFriendly(
                    address = recipientAddress,
                    contactRepository = localContactRepository,
                ).toString()
                val recipientName = resourceProvider.recipientDisplayName(recipientDisplayName)
                return recipientName to recipientAddress.address
            }
        }

        return null to ""
    }

    private fun getMessageSenderForDisplay(sender: String?): String {
        return sender ?: resourceProvider.noSender()
    }
}
