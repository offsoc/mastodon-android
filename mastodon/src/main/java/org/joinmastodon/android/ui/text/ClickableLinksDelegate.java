package org.joinmastodon.android.ui.text;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.widget.TextView;

import org.joinmastodon.android.R;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.model.viewmodel.SectionHeaderListItem;
import org.joinmastodon.android.ui.ExtendedPopupMenu;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.util.List;

import androidx.annotation.NonNull;
import me.grishka.appkit.utils.CustomViewHelper;

public class ClickableLinksDelegate implements CustomViewHelper{

	private final Paint hlPaint;
	private Path hlPath;
	private LinkSpan selectedSpan;
	private final TextView view;

	private final GestureDetector gestureDetector;

	public ClickableLinksDelegate(TextView view) {
		this.view=view;
		hlPaint=new Paint();
		hlPaint.setAntiAlias(true);
		hlPaint.setPathEffect(new CornerPathEffect(dp(3)));
		hlPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		hlPaint.setStrokeWidth(dp(4));
		gestureDetector = new GestureDetector(view.getContext(), new LinkGestureListener(), view.getHandler());
	}

	public boolean onTouch(MotionEvent event) {
		if(event.getAction()==MotionEvent.ACTION_CANCEL){
			// the gestureDetector does not provide a callback for CANCEL, therefore:
			// remove background color of view before passing event to gestureDetector
			resetAndInvalidate();
		}
		return gestureDetector.onTouchEvent(event);
	}

	/**
	 * remove highlighting from span and let the system redraw the view
	 */
	private void resetAndInvalidate() {
		hlPath=null;
		selectedSpan=null;
		view.invalidate();
	}

	public void onDraw(Canvas canvas){
		if(hlPath!=null){
			canvas.save();
			canvas.translate(view.getTotalPaddingLeft(), view.getTotalPaddingTop());
			canvas.drawPath(hlPath, hlPaint);
			canvas.restore();
		}
	}

	@Override
	public Resources getResources(){
		return view.getResources();
	}

	/**
	 * GestureListener for spans that represent URLs.
	 * onDown: on start of touch event, set highlighting
	 * onSingleTapUp: when there was a (short) tap, call onClick and reset highlighting
	 * onLongPress: copy URL to clipboard, let user know, reset highlighting
	 */
	private class LinkGestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onDown(@NonNull MotionEvent event) {
			int padLeft=view.getTotalPaddingLeft(), padRight=view.getTotalPaddingRight(), padTop=view.getTotalPaddingTop(), padBottom=view.getTotalPaddingBottom();
			float x=event.getX(), y=event.getY();
			if(x<padLeft || y<padTop || x>view.getWidth()-padRight || y>view.getHeight()-padBottom)
				return false;
			x-=padLeft;
			y-=padTop;
			Layout l=view.getLayout();
			int line=l.getLineForVertical(Math.round(y));
			int position=l.getOffsetForHorizontal(line, x);

			CharSequence text=view.getText();
			if(text instanceof Spanned s){
				LinkSpan[] spans=s.getSpans(0, s.length()-1, LinkSpan.class);
				for(LinkSpan span:spans){
					int start=s.getSpanStart(span);
					int end=s.getSpanEnd(span);
					if(start<=position && end>position){
						selectedSpan=span;
						hlPath=new Path();
						l.getSelectionPath(start, end, hlPath);
						hlPaint.setColor((span.getColor() & 0x00FFFFFF) | 0x33000000);
						view.invalidate();
						return true;
					}
				}
			}
			return super.onDown(event);
		}

		@Override
		public boolean onSingleTapUp(@NonNull MotionEvent event) {
			if(selectedSpan!=null){
				view.playSoundEffect(SoundEffectConstants.CLICK);
				selectedSpan.onClick(view.getContext());
				resetAndInvalidate();
				return true;
			}
			return false;
		}

		@Override
		public void onLongPress(@NonNull MotionEvent event) {
			if(selectedSpan==null || selectedSpan.getType()!=LinkSpan.Type.URL)
				return;
			view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

			LinkSpan span=selectedSpan;
			final ExtendedPopupMenu[] _menu={null};
			ExtendedPopupMenu menu=new ExtendedPopupMenu(view.getContext(), List.of(
					new SectionHeaderListItem(selectedSpan.getLink().replaceAll("([,.:/? ])", "$1\u2060")),
					new ListItem<>(R.string.open_link, 0, item->{
						span.onClick(view.getContext());
						_menu[0].dismiss();
					}),
					new ListItem<>(R.string.copy, 0, item->{
						ClipboardManager clipboard=(ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
						clipboard.setPrimaryClip(ClipData.newPlainText("", span.getLink()));
						UiUtils.maybeShowTextCopiedToast(view.getContext());
						_menu[0].dismiss();
					})
			));
			_menu[0]=menu;
			menu.showAsDropDown(view, Math.round(event.getX()), Math.round(event.getY())-view.getHeight(), Gravity.TOP | Gravity.LEFT);

			resetAndInvalidate();
		}
	}
}
