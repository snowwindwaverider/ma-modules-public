/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.virtual.vo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.serotonin.json.spi.JsonEntity;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.virtual.rt.ChangeTypeRT;
import com.serotonin.m2m2.virtual.rt.RandomBooleanChangeRT;

@JsonEntity
public class RandomBooleanChangeVO extends ChangeTypeVO {
    public static final TranslatableMessage KEY = new TranslatableMessage("dsEdit.virtual.changeType.random");

    @Override
    public int typeId() {
        return Types.RANDOM_BOOLEAN;
    }

    @Override
    public TranslatableMessage getDescription() {
        return KEY;
    }

    @Override
    public ChangeTypeRT createRuntime() {
        return new RandomBooleanChangeRT();
    }

    //
    //
    // Serialization
    //
    private static final long serialVersionUID = -1;
    private static final int version = 1;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(version);
    }

    private void readObject(ObjectInputStream in) throws IOException {
        in.readInt(); // Read the version. Value is currently not used.
    }
}
