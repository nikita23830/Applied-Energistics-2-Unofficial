package appeng.core.sync.packets;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.network.INetworkManager;
import appeng.container.implementations.ContainerLevelEmitter;
import appeng.core.sync.AppEngPacket;

public class PacketValueConfig extends AppEngPacket
{

	final public String Name;
	final public String Value;

	// automatic.
	public PacketValueConfig(DataInputStream stream) throws IOException {
		Name = stream.readUTF();
		Value = stream.readUTF();
	}

	@Override
	public void serverPacketData(INetworkManager manager, AppEngPacket packet, EntityPlayer player)
	{
		Container c = player.openContainer;

		if ( Name.equals( "LevelEmitter.Value" ) && c instanceof ContainerLevelEmitter )
		{
			ContainerLevelEmitter lvc = (ContainerLevelEmitter) c;
			lvc.setLevel( Integer.parseInt( Value ), player );
			return;
		}

	}

	// api
	public PacketValueConfig(String Name, String Value) throws IOException {
		this.Name = Name;
		this.Value = Value;

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream data = new DataOutputStream( bytes );

		data.writeInt( getPacketID() );
		data.writeUTF( Name );
		data.writeUTF( Value );

		isChunkDataPacket = false;
		configureWrite( bytes.toByteArray() );
	}
}
