from voicevirtualagent_pb2 import VoiceVAResponse
from byova_common_pb2 import OutputEvent, EventInput
import time
from AudioUtils import AudioUtils
from AudioProcessor import AudioProcessor
from EventUtils import EventUtils

class RequestProcessor:

    def __init__(self, conversation_id, virtual_agent_id, tracking_id=None):
        self.conversation_id = conversation_id
        self.virtual_agent_id = virtual_agent_id        
        self.tracking_id = tracking_id
        self.start_time = time.time()
        self.start_of_input_sent = False
        self.can_be_deleted = False
        self.save_audio = False
        self.is_barge_in_enabled = False
        self.audio_processor = AudioProcessor()       

    def process_request(self, request):
        event_type = request.WhichOneof("voice_va_input_type")

        if event_type == "dtmf_input":
            print("Received DTMF input")
            yield from self._process_dtmf_event(request.dtmf_input)

        elif event_type == "event_input":
            yield from self._process_event_input(request.event_input)

        elif event_type == "audio_input":
            yield from self._process_audio_event(request.audio_input.caller_audio)

    def _process_dtmf_event(self, dtmf_event):
        if len(dtmf_event.dtmf_events) == 0:
            response = EventUtils.get_va_response_for_output_event(EventUtils.get_output_event(OutputEvent.EventType.NO_INPUT))
            yield response
        # Below is an example for single-digit input. For multiple digits, delimit with the termination character.
        for dtmf_digit in dtmf_event.dtmf_events:
            if dtmf_digit == 5:
                response = EventUtils.get_va_response_for_output_event(EventUtils.get_output_event(OutputEvent.EventType.TRANSFER_TO_AGENT))
                yield response
            elif dtmf_digit == 6:
                response = EventUtils.get_va_response_for_output_event(EventUtils.get_output_event(OutputEvent.EventType.SESSION_END))
                yield response
            # More cases can be added based on requirements
            else:
                pass

    def _process_event_input(self, event_input):
        if event_input.event_type == EventInput.EventType.SESSION_START:
            print("Received Session start input")
            initial_audio = AudioUtils.get_default_audio()
            yield EventUtils.get_audio_output_events_bytes(initial_audio, "Add transcript of the audio", self.is_barge_in_enabled, VoiceVAResponse.ResponseType.FINAL)
        elif event_input.event_type == EventInput.EventType.SESSION_END:
            print("Received Session end input")
            # Call ends here. Connection cleanup and memory release can be done here.
            yield VoiceVAResponse()

    def _process_audio_event(self, audio_byte):
        yield from self.audio_processor.process_audio_event(audio_byte)
