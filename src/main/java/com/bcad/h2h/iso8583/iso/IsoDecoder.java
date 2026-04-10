package com.bcad.h2h.iso8583.iso;

import com.bcad.h2h.iso8583.exception.IsoMessageParseException;
import com.bcad.h2h.iso8583.iso.packager.BcadIsoPackager;
import com.bcad.h2h.iso8583.iso.packager.FieldDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class IsoDecoder {

    private static final BcadIsoPackager PACKAGER = BcadIsoPackager.getInstance();

    public IsoMessage decode(byte[] rawData) {
        if (rawData == null || rawData.length < 2) {
            throw new IsoMessageParseException("Raw data too short to decode");
        }

        log.debug("Decoding ISO message HEX[{}]", IsoEncoder.bytesToHex(rawData));

        try {
            int offset = 0;

            // Read 2-byte length header
            int msgLength = ((rawData[offset] & 0xFF) << 8) | (rawData[offset + 1] & 0xFF);
            offset += 2;

            if (rawData.length < offset + msgLength) {
                throw new IsoMessageParseException(
                        "Buffer too short: expected " + (offset + msgLength) + " but got " + rawData.length);
            }

            // Read MTI (4 bytes)
            String mti = new String(rawData, offset, 4, StandardCharsets.ISO_8859_1);
            offset += 4;

            IsoMessage message = new IsoMessage(mti);
            message.setRawMessage(rawData);

            // Read primary bitmap (8 bytes)
            byte[] primaryBitmap = new byte[8];
            System.arraycopy(rawData, offset, primaryBitmap, 0, 8);
            offset += 8;

            boolean hasSecondary = (primaryBitmap[0] & 0x80) != 0;

            byte[] secondaryBitmap = new byte[8];
            if (hasSecondary) {
                System.arraycopy(rawData, offset, secondaryBitmap, 0, 8);
                offset += 8;
            }

            // Determine which fields are present
            List<Integer> presentFields = new ArrayList<>();
            for (int bit = 1; bit <= 64; bit++) {
                if (bit == 1) continue;
                if (isBitSet(primaryBitmap, bit)) {
                    presentFields.add(bit);
                }
            }
            if (hasSecondary) {
                for (int bit = 65; bit <= 128; bit++) {
                    if (bit == 65) continue;
                    if (isBitSet(secondaryBitmap, bit - 64)) {
                        presentFields.add(bit);
                    }
                }
            }

            // Decode each field
            for (int fieldNum : presentFields) {
                FieldDefinition def = PACKAGER.getFieldDefinition(fieldNum);
                if (def == null) {
                    log.warn("No field definition for DE{}, cannot decode remaining fields", fieldNum);
                    break;
                }

                int[] consumed = new int[1];
                String value = decodeField(rawData, offset, def, consumed);
                message.setField(fieldNum, value);
                offset += consumed[0];
            }

            log.debug("Decoded ISO message: MTI={}, fields={}", mti, message.getBitmapFields());
            return message;

        } catch (IsoMessageParseException e) {
            throw e;
        } catch (Exception e) {
            throw new IsoMessageParseException("Failed to decode ISO message: " + e.getMessage(), e);
        }
    }

    private boolean isBitSet(byte[] bitmap, int bitPosition) {
        int byteIndex = (bitPosition - 1) / 8;
        int bitIndex = 7 - ((bitPosition - 1) % 8);
        if (byteIndex >= bitmap.length) return false;
        return (bitmap[byteIndex] & (1 << bitIndex)) != 0;
    }

    private String decodeField(byte[] data, int offset, FieldDefinition def, int[] consumed) {
        switch (def.getType()) {
            case NUMERIC, ALPHA, ALPHANUM -> {
                int len = def.getMaxLength();
                String value = new String(data, offset, len, StandardCharsets.ISO_8859_1);
                consumed[0] = len;
                return value;
            }
            case LLVAR -> {
                String lenStr = new String(data, offset, 2, StandardCharsets.ISO_8859_1);
                int len = Integer.parseInt(lenStr.trim());
                String value = new String(data, offset + 2, len, StandardCharsets.ISO_8859_1);
                consumed[0] = 2 + len;
                return value;
            }
            case LLLVAR -> {
                String lenStr = new String(data, offset, 3, StandardCharsets.ISO_8859_1);
                int len = Integer.parseInt(lenStr.trim());
                String value = new String(data, offset + 3, len, StandardCharsets.ISO_8859_1);
                consumed[0] = 3 + len;
                return value;
            }
            case BINARY -> {
                int len = def.getMaxLength();
                byte[] binaryData = new byte[len];
                System.arraycopy(data, offset, binaryData, 0, len);
                consumed[0] = len;
                return IsoEncoder.bytesToHex(binaryData);
            }
            default -> {
                int len = def.getMaxLength();
                consumed[0] = len;
                return new String(data, offset, len, StandardCharsets.ISO_8859_1);
            }
        }
    }
}
