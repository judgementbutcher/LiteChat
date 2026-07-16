package app.litechat.android.data.local

import androidx.room.TypeConverter
import app.litechat.android.data.model.MessageStatus
import app.litechat.android.data.model.ProtocolKind

class Converters {
    @TypeConverter fun protocolToString(value: ProtocolKind): String = value.name
    @TypeConverter fun stringToProtocol(value: String): ProtocolKind = ProtocolKind.valueOf(value)
    @TypeConverter fun statusToString(value: MessageStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): MessageStatus = MessageStatus.valueOf(value)
}
